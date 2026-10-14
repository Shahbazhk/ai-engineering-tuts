"""
LoRA / QLoRA supervised fine-tuning with Hugging Face TRL + PEFT.

Why Python: the training ecosystem (PyTorch, CUDA kernels, bitsandbytes, PEFT, TRL) is Python-first and has no
realistic Java equivalent today. Everything before (dataset building) and after (evaluation, serving) is Java.

Usage (GPU with >= 12 GB VRAM recommended for a 1-3B model in 4-bit):
    python -m venv .venv && . .venv/bin/activate
    pip install "torch" "transformers" "datasets" "peft" "trl" "bitsandbytes" "accelerate"
    python train_lora.py --model meta-llama/Llama-3.2-1B-Instruct \
        --train ../target/sft/train.jsonl --validation ../target/sft/validation.jsonl --out ./adapter

The JSONL files are produced by dev.aieng.p13.DatasetBuilder (chat "messages" format).
This script is not run by the Maven build. Pin library versions in a requirements file for real projects.
"""
import argparse

import torch
from datasets import load_dataset
from peft import LoraConfig
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--model", required=True)
    p.add_argument("--train", required=True)
    p.add_argument("--validation", required=True)
    p.add_argument("--out", default="./adapter")
    p.add_argument("--qlora", action="store_true", help="load the base model in 4-bit (QLoRA)")
    args = p.parse_args()

    tokenizer = AutoTokenizer.from_pretrained(args.model)
    quant = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",            # NormalFloat4: 4-bit codes matched to normally distributed weights
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True,
    ) if args.qlora else None
    model = AutoModelForCausalLM.from_pretrained(
        args.model, quantization_config=quant, torch_dtype=torch.bfloat16, device_map="auto")

    data = load_dataset("json", data_files={"train": args.train, "validation": args.validation})

    lora = LoraConfig(
        r=16,                      # rank of the update matrices
        lora_alpha=32,             # scale = alpha / r
        lora_dropout=0.05,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        task_type="CAUSAL_LM",
    )

    config = SFTConfig(
        output_dir=args.out,
        num_train_epochs=2,
        per_device_train_batch_size=4,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,                 # typical for LoRA; full fine-tunes use ~10x lower
        lr_scheduler_type="cosine",
        warmup_ratio=0.03,
        bf16=True,
        logging_steps=10,
        eval_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
        assistant_only_loss=True,          # learn the answers, not the prompts
        report_to="none",
    )

    trainer = SFTTrainer(
        model=model,
        args=config,
        train_dataset=data["train"],
        eval_dataset=data["validation"],
        processing_class=tokenizer,         # chat template applied to the "messages" column
        peft_config=lora,
    )
    trainer.train()
    trainer.save_model(args.out)            # saves only the adapter (a few MB), not the base model


if __name__ == "__main__":
    main()
