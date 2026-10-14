package dev.aieng.p09;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RagTest {

    static final String VPN_GUIDE = """
            # VPN
            Intro to remote access for employees.
            ## Windows
            Install the client from the software centre.
            ### Proxy
            Set the proxy port to 8443.
            ## Mac
            Install the client from Self Service.
            """;

    @Test
    void fixedSizeChunksOverlap() {
        List<String> chunks = Chunker.fixedSize("a b c d e f g h i j", 4, 1);
        assertEquals(List.of("a b c d", "d e f g", "g h i j"), chunks);
    }

    @Test
    void recursiveSplittingPrefersParagraphBoundaries() {
        String text = "First paragraph is here.\n\nSecond paragraph is a bit longer than the first one.\n\nThird.";
        List<String> chunks = Chunker.recursive(text, 60);
        assertTrue(chunks.stream().allMatch(c -> c.length() <= 60), chunks.toString());
        assertTrue(chunks.contains("First paragraph is here."), chunks.toString());
        assertEquals(List.of("one two", "three"), Chunker.recursive("one two three", 7), "falls back to words");
    }

    @Test
    void headingChunksCarryTheirPath() {
        List<Chunker.Chunk> chunks = Chunker.byHeadings(VPN_GUIDE, 500);
        assertEquals(4, chunks.size());
        assertEquals("VPN > Windows > Proxy", chunks.get(2).headingPath());
        assertEquals("VPN > Mac", chunks.get(3).headingPath());
        assertEquals("IT Handbook > VPN > Windows > Proxy\nSet the proxy port to 8443.",
                Chunker.withHeader("IT Handbook", chunks.get(2)));
    }

    @Test
    void naiveRagGroundsAnswersAndRefusesWithoutCallingTheModel() {
        AtomicInteger modelCalls = new AtomicInteger();
        NaiveRag rag = new NaiveRag(RagTest::keywords, prompt -> {
            modelCalls.incrementAndGet();
            return prompt.contains("8443") ? "Use port 8443 [1]." : "?";
        }, 2, 0.5);
        rag.ingest("kb-1", "IT Handbook", VPN_GUIDE);
        assertEquals(4, rag.chunks());

        NaiveRag.Answer a = rag.ask("Which proxy port does the VPN use on Windows?");
        assertTrue(a.grounded());
        assertEquals("Use port 8443 [1].", a.text());
        assertTrue(a.sources().getFirst().text().contains("8443"), "best source first");

        NaiveRag.Answer none = rag.ask("What is the canteen menu?");
        assertFalse(none.grounded());
        assertEquals(NaiveRag.NO_ANSWER, none.text());
        assertEquals(1, modelCalls.get(), "no model call when nothing relevant was retrieved");
    }

    @Test
    void springRagAdvisorInjectsOnlyPermittedSameSiteContext() {
        SimpleVectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
        store.add(SpringRag.toDocuments("kb-1", "IT Handbook", "site-a", VPN_GUIDE));
        store.add(SpringRag.toDocuments("kb-9", "Other site", "site-b", "# VPN\nThe proxy port is 9999."));
        CapturingChatModel model = new CapturingChatModel("Port 8443.");

        var rag = new SpringRag(model, store, "site-a", List.of(
                (Query q, List<Document> docs) -> docs.stream().filter(d -> !d.getText().contains("Mac")).toList()));
        assertEquals("Port 8443.", rag.ask("VPN proxy port windows"));

        String sent = model.prompts.getLast().getUserMessage().getText();
        assertTrue(sent.contains("8443"), sent);
        assertFalse(sent.contains("9999"), "other site's content must not leak: " + sent);
        assertFalse(sent.contains("Self Service"), "post-processor removed the Mac chunk");
    }

    @Test
    void springRagWithNoRelevantContextTellsTheModelNotToAnswer() {
        SimpleVectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
        store.add(SpringRag.toDocuments("kb-1", "IT Handbook", "site-a", VPN_GUIDE));
        CapturingChatModel model = new CapturingChatModel("I can't answer that.");
        new SpringRag(model, store, "site-a", List.of()).ask("canteen menu today");
        String sent = model.prompts.getLast().getUserMessage().getText();
        assertFalse(sent.contains("8443"));
        assertTrue(sent.toLowerCase(Locale.ROOT).contains("outside your knowledge base")
                || sent.toLowerCase(Locale.ROOT).contains("cannot answer"), sent);
    }

    @Test
    void multiQueryKeepsOriginalAndCleansNumbering() {
        List<String> qs = QueryTransforms.multiQuery("vpn port", 3,
                p -> "1. VPN proxy port number\n2) remote access port\n- vpn port\n\n");
        assertEquals(List.of("vpn port", "VPN proxy port number", "remote access port"), qs);
        assertEquals("How do I install the VPN on a Mac?",
                QueryTransforms.standalone("user: How do I install the VPN on Windows?", "and on Mac?",
                        p -> " How do I install the VPN on a Mac? "));
    }

    @Test
    void liferayIngestionPagesThroughStructuredContentsAndFiltersByUserPermission() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://liferay.local");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sites/20121/structured-contents?page=1")))
                .andRespond(withSuccess("""
                        {"items":[{"id":101,"title":"VPN setup","friendlyUrlPath":"vpn-setup","dateModified":"2026-09-01T10:00:00Z",
                          "contentFields":[{"name":"content","contentFieldValue":{"data":"<p>Install the client.</p><p>Port&nbsp;8443</p>"}}]}],
                         "page":1,"lastPage":2}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("page=2")))
                .andRespond(withSuccess("""
                        {"items":[{"id":102,"title":"HR salaries","friendlyUrlPath":"salaries","dateModified":"2026-09-02T10:00:00Z",
                          "contentFields":[{"name":"content","contentFieldValue":{"data":"<p>Confidential.</p>"}}]}],
                         "page":2,"lastPage":2}""", MediaType.APPLICATION_JSON));
        List<Document> docs = LiferayContent.fetchSite(builder.build(), "20121");
        server.verify();
        assertEquals(2, docs.size());
        assertTrue(docs.getFirst().getText().startsWith("VPN setup\nInstall the client.\nPort 8443"), docs.getFirst().getText());
        assertEquals("/w/vpn-setup", docs.getFirst().getMetadata().get("url"));

        RestClient.Builder userBuilder = RestClient.builder().baseUrl("http://liferay.local");
        MockRestServiceServer userServer = MockRestServiceServer.bindTo(userBuilder).build();
        userServer.expect(requestTo(org.hamcrest.Matchers.containsString("/structured-contents/101")))
                .andRespond(withSuccess("{\"id\":101}", MediaType.APPLICATION_JSON));
        userServer.expect(requestTo(org.hamcrest.Matchers.containsString("/structured-contents/102")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        List<Document> visible = LiferayContent.permissionFilter(userBuilder.build()).process(new Query("q"), docs);
        assertEquals(List.of("101"), visible.stream().map(d -> d.getMetadata().get("contentId")).toList());
    }

    // --- test doubles ----------------------------------------------------------------------------------

    static final String[] WORDS = {"vpn", "proxy", "port", "windows", "mac", "install", "client", "canteen", "menu"};

    static float[] keywords(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        float[] v = new float[WORDS.length + 1];
        v[WORDS.length] = 0.05f;
        for (int i = 0; i < WORDS.length; i++) {
            v[i] = t.contains(WORDS[i]) ? 1 : 0;
        }
        return v;
    }

    static final class KeywordEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> out = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                out.add(new Embedding(keywords(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(out);
        }

        @Override
        public float[] embed(Document document) {
            return keywords(document.getText());
        }
    }

    static final class CapturingChatModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();
        final String answer;

        CapturingChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        }
    }
}
