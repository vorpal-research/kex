package org.jetbrains.research.kex.test.spider.okhttp.client;


import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.io.IOException;

@SuppressWarnings("DuplicatedCode")
public class Main {
    public static void main(String[] args) {

        OkHttpClient client = new OkHttpClient();
        String url = "";

        Request.Builder builder = new Request.Builder();
        builder.url(url);
        builder.url(url);
        Request request = builder.build();
        try {
            client.newCall(request).execute().body().string();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
