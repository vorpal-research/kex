package org.jetbrains.research.kex.test.spider.vk;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.events.longpoll.GroupLongPollApi;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.Message;

import java.util.ArrayList;
import java.util.List;

public class Main {
    final static int communityId = 208922033;
    final static String token = System.getenv("token");

    public static void main(String[] args) {
        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        vk.setVersion("5.102");

        GroupActor actor = new GroupActor(communityId, token);
        MessageHandler handler = new MessageHandler(vk, actor);
        handler.run();
    }
}

class MessageHandler extends GroupLongPollApi {
    private final static List<String> phrases = new ArrayList<>();
    private final VkApiClient vk;
    private final GroupActor actor;

    MessageHandler(VkApiClient client, GroupActor actor) {
        super(client, actor, 25);
        this.vk = client;
        this.actor = actor;

        phrases.add("Hello there!");
        phrases.add("Привет всем!");
        phrases.add("Hola todos!");
        phrases.add("Hallo an alle!");
    }

    @Override
    protected void messageNew(Integer groupId, Message message) {
        String text = message.getText();
        if (text.equals("/phrase")) {
            int idx = message.getId() % (phrases.size());
            try {
                vk.messages()
                        .send(actor)
                        .message(phrases.get(idx))
                        .peerId(message.getFromId())
                        .randomId(message.getId())
                        .execute();
            } catch (ApiException | ClientException e) {
                e.printStackTrace();
            }
        } else {
            try {
                vk.messages()
                        .send(actor)
                        .message("unknown command")
                        .peerId(message.getFromId())
                        .randomId(message.getId())
                        .execute();
            } catch (ApiException | ClientException e) {
                e.printStackTrace();
            }
        }
    }
}