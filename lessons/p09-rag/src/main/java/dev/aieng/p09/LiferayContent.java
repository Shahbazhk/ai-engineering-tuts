package dev.aieng.p09;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RAG over Liferay DXP web content through the Headless Delivery API.
 * Ingestion reads structured contents page by page with a service account; retrieval re-checks every candidate
 * against Liferay AS THE END USER, so the assistant can never quote content the user is not allowed to open.
 */
public final class LiferayContent {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private LiferayContent() {
    }

    // region ingest
    /** GET /o/headless-delivery/v1.0/sites/{siteId}/structured-contents, following pagination. */
    public static List<Document> fetchSite(RestClient serviceAccountClient, String siteId) {
        List<Document> docs = new ArrayList<>();
        int page = 1;
        int lastPage;
        do {
            String body = serviceAccountClient.get()
                    .uri("/o/headless-delivery/v1.0/sites/{siteId}/structured-contents?page={page}&pageSize=50&fields="
                         + "id,title,contentFields,friendlyUrlPath,dateModified", siteId, page)
                    .retrieve()
                    .body(String.class);
            JsonNode json = JSON.readTree(body);
            for (JsonNode item : json.path("items")) {
                StringBuilder text = new StringBuilder();
                for (JsonNode field : item.path("contentFields")) {
                    text.append(stripHtml(field.path("contentFieldValue").path("data").asString())).append("\n\n");
                }
                String id = item.path("id").asString();
                docs.addAll(SpringRag.toDocuments("liferay-sc-" + id, item.path("title").asString(), siteId, text.toString())
                        .stream()
                        .map(d -> new Document(d.getId(), d.getText(), withExtra(d.getMetadata(), Map.of(
                                "contentId", id,
                                "url", "/w/" + item.path("friendlyUrlPath").asString(),
                                "dateModified", item.path("dateModified").asString()))))
                        .toList());
            }
            lastPage = json.path("lastPage").asInt(page);
            page++;
        } while (page <= lastPage);
        return docs;
    }
    // endregion

    // region permissions
    /**
     * Post-retrieval permission check: for each candidate, ask Liferay (with the USER's token) whether the user can
     * read the source content. Liferay applies its own permission model (roles, sites, workflow status); we only
     * keep documents that return 200. ponytail: one HTTP call per candidate; batch or cache per request at scale.
     */
    public static DocumentPostProcessor permissionFilter(RestClient userClient) {
        return (Query query, List<Document> documents) -> documents.stream()
                .filter(d -> userCanRead(userClient, String.valueOf(d.getMetadata().get("contentId"))))
                .toList();
    }

    static boolean userCanRead(RestClient userClient, String contentId) {
        HttpStatusCode status = userClient.get()
                .uri("/o/headless-delivery/v1.0/structured-contents/{id}?fields=id", contentId)
                .exchange((request, response) -> response.getStatusCode());
        return status.is2xxSuccessful(); // 403 and 404 both mean "not for this user"
    }
    // endregion

    static String stripHtml(String html) {
        String text = html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</h\\d>", "\n").replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("[ \\t]+", " ");
        return text.lines().map(String::strip).filter(l -> !l.isEmpty()).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static Map<String, Object> withExtra(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> m = new java.util.HashMap<>(base);
        m.putAll(extra);
        return m;
    }
}
