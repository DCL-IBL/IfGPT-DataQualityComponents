# IfGPT DATASET Qiality Components

## Objectives of the project IfGPT
The **IfGPT Qiality Pipeline** is developed within the project **IfGPT: Infrastructure for Fine-tuning Pre-trained Large Language Models** which aims to establish a freely accessible infrastructure for the selection and pre-processing of large datasets for Bulgarian as well as tailored data for particular industries and fine-tuning suitable freely available large language models for specific purposes.

## IfGPT Dataset Quality Pipeline

Modular Java pipeline to process and add new text documents to the IfGPT Dataset,
which includes cleaning, deduplication and quality evaluation of Bulgarian texts.
The pipeline includes:

- **Source-specific extraction** — metadata and plain text extracted from heterogeneous
  corpora (MARCELL, CURLICAT, BulNC, Wikipedia), each handled by a dedicated class
  that implements the shared `SourceProcessor` interface and extends `BaseSourceProcessor`
- **Sentence splitting** — `BulgarianSentenceSplitter` wraps the Apache OpenNLP Bulgarian
  UD sentence-detection model, splitting every document into a sentence-per-line sidecar
  file used by all downstream stages
- **Boilerplate cleaning** — `FileCleanProcessor` learns site-specific boilerplate from a
  sample directory (lines appearing in ≥ 50 % of files) and removes them alongside
  hardcoded patterns for HTML tags, navigation menus, URLs, cookie banners, and more
- **MinHash / LSH deduplication** — `DeduplicationProcessor` builds a MinHash signature
  index over the full existing corpus and detects near-duplicate sentences in the new
  batch (Jaccard ≥ 0.90), writing a ranked TSV report and optionally removing duplicates
- **Per-sentence PII scoring** — `PIIDetector` runs every sentence through the Phileas
  engine (names, emails, phone numbers, IBANs, IP addresses, etc.) and stores the
  proportion of flagged tokens as the `PersonallyIdentifiableInformation` vector in metadata
- **Per-sentence bias scoring** — `BiasAnalyser` matches tokens against `BiasLexicon`
  (3 787-entry Bulgarian Bias Dictionary v4) to detect signal–evaluator pairs across five
  categories (gender, race/ethnicity, religion, disability, appearance), storing the
  per-sentence coverage ratio as the `BiasedInformation` vector in metadata

The full schema is enforced by `DocumentMetadata` (15 mandatory + 8 optional fields) and
the complete flow is orchestrated by `IfGPTPipeline`, with `IfGPTDatasetProcessor` as the
main entry point.

```
source processors → sentence split → clean → dedup → PII + bias → counts → persist
```

## License

Creative Commons Attribution 4.0 International (CC-BY-4.0)
__________________________________________

This work is part of the project **Infrastructure for Fine-tuning Pre-trained Large Language Models**, Grant Agreement No. ПВУ – 55 from 12.12.2024 /BG-RRP-2.017-0030-C01/.

https://ifgpt.dcl.bas.bg/en/
