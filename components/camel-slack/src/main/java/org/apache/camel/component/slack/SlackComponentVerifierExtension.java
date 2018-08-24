/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.slack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class SlackComponentVerifierExtension extends DefaultComponentVerifierExtension {

    public SlackComponentVerifierExtension() {
        this("slack");
    }

    public SlackComponentVerifierExtension(String scheme) {
        super(scheme);
    }

    // *********************************
    // Parameters validation
    // *********************************
    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS);

        if (ObjectHelper.isEmpty(parameters.get("token")) && ObjectHelper.isEmpty(parameters.get("webhookUrl"))) {
            builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.GENERIC, "You must specify a webhookUrl (for producer) or a token (for consumer)").parameterKey("webhookUrl").parameterKey("token").build());
        }
        if (ObjectHelper.isNotEmpty(parameters.get("token")) && ObjectHelper.isNotEmpty(parameters.get("webhookUrl"))) {
            builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.GENERIC, "You must specify a webhookUrl (for producer) or a token (for consumer). You can't specify both.").parameterKey("webhookUrl").parameterKey("token").build());
        }
        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************
    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY).error(parameters, this::verifyCredentials).build();
    }

    private void verifyCredentials(ResultBuilder builder, Map<String, Object> parameters) {

        String webhookUrl = (String)parameters.get("webhookUrl");

        if (ObjectHelper.isNotEmpty(webhookUrl)) {

            try {
                HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
                HttpPost httpPost = new HttpPost(webhookUrl);

                // Build Helper object
                SlackMessage slackMessage;
                slackMessage = new SlackMessage();
                slackMessage.setText("Test connection");

                // Set the post body
                String json = asJson(slackMessage);
                StringEntity body = new StringEntity(json);

                // Do the post
                httpPost.setEntity(body);
                
                HttpResponse response = client.execute(httpPost);

                // 2xx is OK, anything else we regard as failure
                if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() > 299) {
                    builder
                        .error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid webhookUrl").parameterKey("webhookUrl").build());
                }
            } catch (Exception e) {
                builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid webhookUrl").parameterKey("webhookUrl").build());
            }
        } else if (ObjectHelper.isNotEmpty((String)parameters.get("token"))) {
            String token = (String)parameters.get("token");

            try {
                HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
                HttpPost httpPost = new HttpPost("https://slack.com/api/channels.list");

                List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
                params.add(new BasicNameValuePair("token", token));
                httpPost.setEntity(new UrlEncodedFormEntity(params));

                HttpResponse response = client.execute(httpPost);

                String jsonString = readResponse(response.getEntity().getContent());
                if (response.getStatusLine().getStatusCode() < 200 || response.getStatusLine().getStatusCode() > 299) {
                    builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid token").parameterKey("token").build());
                }
                JSONParser parser = new JSONParser();
                JSONObject obj = (JSONObject)parser.parse(jsonString);
                if (obj.get("ok") != null && obj.get("ok").equals(false)) {
                    builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid token").parameterKey("token").build());
                }
            } catch (Exception e) {
                builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid token").parameterKey("token").build());
            }

        }
    }

    private String readResponse(InputStream s) throws IOException, UnsupportedEncodingException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = s.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        String jsonString = result.toString(StandardCharsets.UTF_8.name());
        return jsonString;
    }

    protected String asJson(SlackMessage message) {
        Map<String, Object> jsonMap = new HashMap<>();

        // Put the values in a map
        jsonMap.put("text", message.getText());

        // Generate a JSONObject
        JSONObject jsonObject = new JSONObject(jsonMap);

        // Return the string based on the JSON Object
        return JSONObject.toJSONString(jsonObject);
    }

}
