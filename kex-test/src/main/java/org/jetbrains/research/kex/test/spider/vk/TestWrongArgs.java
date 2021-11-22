package org.jetbrains.research.kex.test.spider.vk;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.events.longpoll.GroupLongPollApi;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.messages.Message;


public class TestWrongArgs {
    final static String token = System.getenv("token");

    public static void main(String[] args) {
        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        vk.setVersion("5.130");  // unsupported version

        GroupActor actor = new GroupActor(Values.communityId, token);
        MessageHandler handler = new MessageHandler(vk, actor);
        handler.run();
    }

    static class MessageHandler extends GroupLongPollApi {
        private final VkApiClient vk;
        private final GroupActor actor;

        MessageHandler(VkApiClient client, GroupActor actor) {
            super(client, actor, 25);
            this.vk = client;
            this.actor = actor;
        }

        @Override
        protected void messageNew(Integer groupId, Message message) {
            String text = message.getText();
            int peerId = message.getPeerId();
            // programmer forgot write this
//            if (peerId <= 0) {
//                throw new IllegalArgumentException("peerId must be positive");
//            }

            if (text.equals("/phrase")) {
                try {
                    vk.messages()
                            .send(actor)
                            .message(Values.phrase)
                            .peerId(peerId)
                            .randomId(message.getId())
                            .execute();
                } catch (ApiException | ClientException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    vk.messages()
                            .send(actor)
                            .message("") // message can't be empty
                            .peerId(peerId)
                            .randomId(message.getId())
                            .execute();
                } catch (ApiException | ClientException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class Values {
        final static int communityId = -208922033; // negative id
        final static String phrase = "Hello world!";
    }
}