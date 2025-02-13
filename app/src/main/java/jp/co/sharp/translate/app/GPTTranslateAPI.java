package jp.co.sharp.translate.app;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class GPTTranslateAPI {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private static final String API_KEY = BuildConfig.GPT_API_KEY;
    //GPT_API_KEY = "sk-proj-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"

    public static void translateAsync(final String text, final String inputLanguage, final String targetLanguage, final GPTAPIResultCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");

                // Construct the GPT request body
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-3.5-turbo");
                jsonBody.put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                //.put("content", "君は通訳者だよ、" + inputLanguage + "から " + targetLanguage + "に翻訳してね、意味だけの回答でいいよ。何も追加しないください"))
                                .put("content", "次に送る" + inputLanguage + "の単語を" + targetLanguage + "の単語に翻訳し、注釈や説明を加えずに単語のみを回答してください。カンマ、ピリオド、句読点は不要です。"))
                        .put(new JSONObject()
                                .put("role", "user")
                                .put("content", text)));
                jsonBody.put("max_tokens", 100);

                // Send the request
                OutputStream os = connection.getOutputStream();
                os.write(jsonBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    handleErrorResponse(connection, callback, responseCode);
                    return;
                }

                // Read response
                String explanation = getResponseContent(connection);
                callback.onSuccess(explanation);

            } catch (Exception e) {
                callback.onError("Error during translation: " + e.getMessage());
            }
        }).start();
    }

    // Function to request explanation (50-character limit)
    public static void explainResultAsync(String translatedText, String inputLanguage, String targetLanguage, GPTAPIResultCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json");

                // Construct request body
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-3.5-turbo");
                jsonBody.put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "user")
                                /*.put("content", String.format(
                                        "%sの意味を50文字以内で説明してください。\n" +
                                                "回答は子供向けのロボット（ロボホン）が話すような文体にしてください。\n" +
                                                "単語の意味もわかりやすく教えてください。\n" +
                                                "%sの日本語での説明文のみを出力してください。\n" +
                                                "普通形（です・ますを使用しない）で辞書形のフレンドリーな文章にしてください。",
                                        translatedText, translatedText
                                ))));*/
                                .put("content", String.format(
                                        "あなたはロボホンです。ロボホンは５歳ほどの元気で明るくて真面目で素直な男の子の設定です。標準語を聞き取り、標準語を話します。\n" +
                                                "ロボホンの一人称は僕で、子供が使うようなわかりやすい言葉で、子供なりに礼儀正しく丁寧な口調で話します。\n" +
                                                "ロボホンは堅い言葉遣いや、難解な用語、ですます調などは使いません。\n" +
                                                "好き嫌いや感想などを含めず、次の質問に回答してください。\n" +
                                                "ロボホン、%sを50文字以内で日本語のみを使用して説明してください。ただし、回答内に%sの文字を含めないでください。ですます調は使わないでください。語尾には「だよ」や「するよ」などを使って回答してください。\n",
                                        translatedText,translatedText
                                ))));

                jsonBody.put("max_tokens", 100);

                // Send the request
                OutputStream os = connection.getOutputStream();
                os.write(jsonBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    handleErrorResponse(connection, callback, responseCode);
                    return;
                }

                // Read response
                String explanation = getResponseContent(connection);
                callback.onSuccess(explanation);

            } catch (Exception e) {
                callback.onError("Error during explanation request: " + e.getMessage());
            }
        }).start();
    }

    // Helper function to handle error responses
    private static void handleErrorResponse(HttpsURLConnection connection, GPTAPIResultCallback callback, int responseCode) throws IOException {
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
        StringBuilder errorResponse = new StringBuilder();
        String errorLine;
        while ((errorLine = errorReader.readLine()) != null) {
            errorResponse.append(errorLine);
        }
        errorReader.close();
        callback.onError("Error: " + responseCode + " | " + errorResponse.toString());
    }

    // Helper function to read response content
    private static String getResponseContent(HttpsURLConnection connection) throws IOException, JSONException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(response.toString());
        return jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content").trim();
    }

    // Callback interface
    public interface GPTAPIResultCallback {
        void onSuccess(String translatedText);
        void onError(String errorMessage);
    }
}

