# IfGPT DATASET Qiality Components

## Objectives of the project IfGPT
The **IfGPT Qiality Pipeline** is developed within the project **IfGPT: Infrastructure for Fine-tuning Pre-trained Large Language Models** which aims to establish a freely accessible infrastructure for the selection and pre-processing of large datasets for Bulgarian as well as tailored data for particular industries and fine-tuning suitable freely available large language models for specific purposes.

## IfGPT DATASET Qiality Pipeline

Modular Java pipeline to process and add new text documents to the IfGPT Dataset, which includes cleaning, deduplication and quality evaluation of Bulgarian texts. 

The pipelines includes:

- Source-specific processing and extraction of metadata (MARCELL, CURLICAT, BulNC, Wikipedia, etc.), 
- Sentence splitting (via OpenNLP), 
- Cleaning, e.g. boilerplate removal, navigarion components, etc., 
- MinHash/LSH deduplication, 
- Per-sentence PII,
- Lexically based bias scoring.

## License

Creative Commons Attribution 4.0 International (CC-BY-4.0)
__________________________________________

This work is part of the project **Infrastructure for Fine-tuning Pre-trained Large Language Models**, Grant Agreement No. ПВУ – 55 from 12.12.2024 /BG-RRP-2.017-0030-C01/.

https://ifgpt.dcl.bas.bg/en/
