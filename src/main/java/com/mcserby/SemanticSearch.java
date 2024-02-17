package com.mcserby;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

//@Service
public class SemanticSearch {

    private final VectorStore  vectorStore;


    public SemanticSearch(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void storeDocuments(List<Document> aiDocs){
        vectorStore.add(aiDocs);
    }

    public List<Document> getSimilarDoc(String query){
        return vectorStore.similaritySearch(query);
    }


}
