package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class CrptApi{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore requestSemaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestSemaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(() -> {
            int availablePermits = requestSemaphore.availablePermits();
            if (availablePermits < requestLimit) {
                requestSemaphore.release(requestLimit - availablePermits);
            }
        }, 0, 1, timeUnit);
    }

    public void createDocument(DocumentModel document, String signature, int numThread) {
        try {
            requestSemaphore.acquire();

            URI uri = new URI("https://ismp.crpt.ru/api/v3/lk/documents/create");
            String payload = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + signature)
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();
            System.out.println("Thread№" + numThread + " " + responseBody + " status " + statusCode);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            requestSemaphore.release();
        }
    }

    public void Close(){
        scheduler.close();
    }

    public static void main(String[] args) throws InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);
        String signature = "signature";
        List<Thread> threads = new ArrayList<>();

        for(int i = 0; i < 50; ++i){
            int k = i;
            threads.add(new Thread(() -> {
                crptApi.createDocument(getData(), signature, k);
            }));
        }

        for(var i : threads){
            i.start();
        }

        for(var i : threads){
            i.join();
        }

        crptApi.Close();
    }

    public static DocumentModel getData(){
        List<ProductModel> products = new ArrayList<>();

        products.add(
            new ProductModel(
                "string",
                "2020-01-23",
                "string",
                "string",
                "string",
                "2020-01-23",
                "string",
                "string",
                "string"
            )
        );
        return new DocumentModel(
            new DescriptionModel("string"),
            "string",
            "string",
            "LP_INTRODUCE_GOODS",
            true,
            "string",
            "string",
            "string",
            "2020-01-23",
            "string",
            products,
            "2020-01-23",
            "string"
        );
    }

    public record DocumentModel(
        DescriptionModel description,
        String doc_id,
        String doc_status,
        String doc_type,
        boolean importRequest,
        String owner_inn,
        String participant_inn,
        String producer_inn,
        String production_date,
        String production_type,
        List<ProductModel> products,
        String reg_date,
        String reg_number
    ){}

    public record ProductModel(
        String certificate_document,
        String certificate_document_date,
        String certificate_document_number,
        String owner_inn,
        String producer_inn,
        String production_date,
        String tnved_code,
        String uit_code,
        String uitu_code
    ){}

    public record DescriptionModel(
        String participantInn
    ){}
}