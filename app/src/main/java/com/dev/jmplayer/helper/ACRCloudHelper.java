package com.dev.jmplayer.helper; // Certifique-se que o pacote está correto

import android.content.Context;
import android.util.Log;

import com.acrcloud.rec.ACRCloudClient;
import com.acrcloud.rec.ACRCloudConfig;
import com.acrcloud.rec.ACRCloudResult; // Importação correta
import com.acrcloud.rec.IACRCloudListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ACRCloudHelper {

    private static final String TAG = "ACRCloudHelper";

    private final Context context;
    private ACRCloudClient acrCloudClient;
    private ACRCloudConfig acrCloudConfig;
    private final ACRCloudListener listener;
    private boolean isRecognizing = false;

    // A nossa interface de listener personalizada
    public interface ACRCloudListener {
        void onResult(String title, String artist);
        void onError(String message);
    }

    public ACRCloudHelper(Context context, String host, String accessKey, String accessSecret, ACRCloudListener listener) {
        this.context = context;
        this.listener = listener;
        init(host, accessKey, accessSecret);
    }

    private void init(String host, String accessKey, String accessSecret) {
        acrCloudConfig = new ACRCloudConfig();
        acrCloudConfig.acrcloudListener = new IACRCloudListener() {


            @Override
            public void onResult(ACRCloudResult acrCloudResult) {
                isRecognizing = false;
                String result = acrCloudResult.getResult(); // Obter a string JSON do objeto

                if (result != null) {
                    Log.d(TAG, "Resultado bruto da API: " + result);
                    parseResult(result); // Enviar a string para o nosso parser
                } else {
                    listener.onError("Nenhum resultado encontrado.");
                }
            }

            @Override
            public void onVolumeChanged(double volume) {
                // Pode ser usado para mostrar um visualizador de som no futuro
            }
        };

        acrCloudConfig.context = this.context;
        acrCloudConfig.host = host;
        acrCloudConfig.accessKey = accessKey;
        acrCloudConfig.accessSecret = accessSecret;

        acrCloudConfig.recMode = ACRCloudConfig.ACRCloudRecMode.REC_MODE_REMOTE;

        acrCloudClient = new ACRCloudClient();
        boolean success = acrCloudClient.initWithConfig(acrCloudConfig);
        if (!success) {
            Log.e(TAG, "Falha ao inicializar o ACRCloud SDK");
            listener.onError("Falha ao inicializar o serviço de reconhecimento.");
        }
    }

    public void startRecognition() {
        if (acrCloudClient != null && !isRecognizing) {
            isRecognizing = true;
            if (acrCloudClient.startRecognize()) {
                Log.d(TAG, "Iniciando reconhecimento...");
            } else {
                isRecognizing = false;
                listener.onError("Falha ao iniciar o reconhecimento.");
            }
        }
    }

    // Este é o metodo que o Presenter chama
    public void stopRecognition() {
        if (acrCloudClient != null && isRecognizing) {
            //
            //acrCloudClient.stop();
            isRecognizing = false;
            Log.d(TAG, "Reconhecimento parado pelo utilizador.");
        }
    }

    public void cancel() {
        if (acrCloudClient != null && isRecognizing) {
            acrCloudClient.cancel();
            isRecognizing = false;
            Log.d(TAG, "Reconhecimento cancelado.");
        }
    }

    // O nosso metodo de parsing interno
    private void parseResult(String result) {
        try {
            JSONObject json = new JSONObject(result);
            JSONObject status = json.getJSONObject("status");
            int code = status.getInt("code");

            if (code == 0) { // Código 0 significa sucesso
                JSONObject metadata = json.getJSONObject("metadata");
                if (metadata.has("music")) {
                    JSONArray music = metadata.getJSONArray("music");
                    if (music.length() > 0) {
                        JSONObject track = music.getJSONObject(0);
                        String title = track.getString("title");
                        JSONArray artists = track.getJSONArray("artists");
                        String artist = "Artista Desconhecido";
                        if (artists.length() > 0) {
                            artist = artists.getJSONObject(0).getString("name");
                        }
                        // Chama o nosso listener personalizado com os dados limpos
                        listener.onResult(title, artist);
                        return;
                    }
                }
            }
            // Se não encontrou música ou o código não é 0
            listener.onError("Não foi possível identificar a música.");

        } catch (JSONException e) {
            Log.e(TAG, "Erro ao processar o JSON: " + e.getMessage());
            listener.onError("Erro ao processar a resposta.");
        }
    }
}