package org.jetbrains.research.kex.test.spider.okhttp.client;


import com.squareup.okhttp.*;

import java.io.IOException;

@SuppressWarnings("DuplicatedCode")
public class Main {
    public static void main(String[] args) {
        String url = "https://example.com";
        OkHttpClient client = new OkHttpClient();
        Request.Builder builder = new Request.Builder();
        if (!args[0].equals("")) {
            url = args[0];
            builder.url(url); // wrong!
        }
        builder.url(url); // error occurs here
        Request request = builder.build();

        try {
            Call call = client.newCall(request);
            Response resp = call.execute();
            ResponseBody body = resp.body();
            String str = body.string();
            System.out.println("response is " + str);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
