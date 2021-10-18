package org.jetbrains.research.kex.test.spider.okhttp.client;


import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.io.IOException;

@SuppressWarnings("DuplicatedCode")
public class Main {
    public static void main(String[] args) {
        if (args[0].equals("")) {
            OkHttpClient client = new OkHttpClient();
            String url = "";
            HttpUrl httpUrl = new HttpUrl.Builder().build();

            Request request = new Request.Builder()
                    .url(httpUrl)
                    .url(httpUrl) // error occurs here
                    .build();
            try {
                client.newCall(request).execute().body().string();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            OkHttpClient client = new OkHttpClient();
            String url = "http://example.com";

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try {
                client.newCall(request).execute().body().string();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
