package jp.co.sharp.translate.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;//追加1/17 multilingualからのコピペ
import android.view.View;//追加1/17 multilingualからのコピペ
import android.view.WindowManager;
import android.widget.Button;//追加1/17 multilingualからのコピペ
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;

import java.util.List;
import java.util.Locale;//追加1/17 multilingualからのコピペ
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.Arrays;
import android.util.Log;

import jp.co.sharp.android.voiceui.VoiceUIManager;
import jp.co.sharp.android.voiceui.VoiceUIVariable;
import jp.co.sharp.translate.app.voiceui.ScenarioDefinitions;
import jp.co.sharp.translate.app.voiceui.VoiceUIListenerImpl;
import jp.co.sharp.translate.app.voiceui.VoiceUIManagerUtil;
import jp.co.sharp.translate.app.voiceui.VoiceUIVariableUtil;//追加1/17 multilingualからのコピペ


/**
 * 音声UIを利用した最低限の機能だけ実装したActivity.
 */

public class MainActivity extends Activity implements VoiceUIListenerImpl.ScenarioCallback {
    public static final String TAG = MainActivity.class.getSimpleName();

    /**
     * 音声UI制御.
     */
    private VoiceUIManager mVUIManager = null;
    /**
     * 音声UIイベントリスナー.
     */
    private VoiceUIListenerImpl mVUIListener = null;
    /**
     * ホームボタンイベント検知.
     */
    private HomeEventReceiver mHomeEventReceiver;
    private Spinner spinner_input;//スピナーの入力をいろんな関数で得るためここで宣言
    private Spinner spinner_target;//スピナーの入力をいろんな関数で得るためここで宣言
    private EditText inputTextValue;
    private TextView outputTextValue;
    private final int max_length = 100;//翻訳前後の文の長さの許容限界
    private String inputLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate()");
        setContentView(R.layout.activity_main);

        //タイトルバー設定.
        setupTitleBar();

        //ホームボタンの検知登録.
        mHomeEventReceiver = new HomeEventReceiver();
        IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mHomeEventReceiver, filterHome);

        // Prevent the keyboard from showing up on app start
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // 単語変数を取得
        inputTextValue = (EditText) findViewById(R.id.input_text_value);
        outputTextValue = (TextView) findViewById(R.id.output_text_value);

        // 翻訳ボタン表示
        Button voiceTranslateButton = (Button) findViewById(R.id.voice_translate_button);
        voiceTranslateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTextProcessing();
            }
        });

        // 終了ボタン取得
        Button finishButton = (Button) findViewById(R.id.finish_app_button);

        // 終了ボタンの処理
        finishButton.setOnClickListener(view -> {
            // Finish the current activity
            finish();
        });

        //入力側の言語切り替えボックスを作成し、対応言語一覧をセット
        spinner_input = (Spinner) findViewById(R.id.spinner_input);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.languages, R.layout.spinner_layout);
        adapter.setDropDownViewResource(R.layout.spinner_layout);
        spinner_input.setAdapter(adapter);//最初に表示されるのはlanguagesの一番上にある英語

        // リスナーを登録
        spinner_input.setOnItemSelectedListener(new OnItemSelectedListener() {
            //　アイテムが選択された時
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view, int position, long id) {
                Spinner spinner = (Spinner)parent;
                String item = (String)spinner.getSelectedItem();
                if(Objects.equals(item, "英語")){
                    VoiceUIManagerUtil.setAsr(mVUIManager, Locale.US);//音声認識の言語設定
                    VoiceUIManagerUtil.setTts(mVUIManager, Locale.US);//音声発話の言語設定
                    inputLanguage = "英語";
                }else if(Objects.equals(item, "中国語")){
                    VoiceUIManagerUtil.setAsr(mVUIManager, Locale.CHINA);//音声認識の言語設定
                    VoiceUIManagerUtil.setTts(mVUIManager, Locale.CHINA);//音声発話の言語設定
                    inputLanguage = "中国語";
                }else if(Objects.equals(item, "日本語")){
                    VoiceUIManagerUtil.setAsr(mVUIManager, Locale.JAPAN);//音声認識の言語設定
                    VoiceUIManagerUtil.setTts(mVUIManager, Locale.JAPAN);//音声発話の言語設定
                    inputLanguage = "日本語";
                }
            }

            //　アイテムが選択されなかった
            public void onNothingSelected(AdapterView<?> parent) {
                //
            }
        });

        //出力側の言語切り替えボックスを作成し、対応言語一覧をセット
        spinner_target = (Spinner) findViewById(R.id.spinner_target);
        adapter = ArrayAdapter.createFromResource(this, R.array.languages, R.layout.spinner_layout);
        adapter.setDropDownViewResource(R.layout.spinner_layout);
        spinner_target.setAdapter(adapter);//最初に表示されるのはlanguagesの一番上にある英語

    }

    /**
     * Handle the text processing when the button is clicked.
     */
    private void handleTextProcessing() {
        //入力テキストを取得
        String original_word = inputTextValue.getText().toString().trim();

        //翻訳先言語を取得
        String targetLanguage = spinner_target.getSelectedItem().toString();
        //翻訳先言語をspeakシナリオの手が届くpメモリに送る
        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TARGET, targetLanguage);

        //speakシナリオを開始させる
        startSpeakScenario(original_word,targetLanguage);

        //ボタンからスピークを開始するときはspeech_okと身振り手振りのないトピックにしたほうがいいかも

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume()");

        //VoiceUIManagerインスタンス生成.
        if(mVUIManager == null){
            mVUIManager = VoiceUIManager.getService(this);
        }
        //VoiceUIListenerインスタンス生成.
        if (mVUIListener == null) {
            mVUIListener = new VoiceUIListenerImpl(this);
        }
        //VoiceUIListenerの登録.
        VoiceUIManagerUtil.registerVoiceUIListener(mVUIManager, mVUIListener);

        //Scene有効化.
        VoiceUIManagerUtil.enableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);

        inputLanguage = "日本語";//TASK 仮
        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_INPUT, inputLanguage);

        //アプリ起動時に翻訳APIのテストをして発話を実行
        final String test_translated_word = translateSync("りんご", "en");//適当な単語を英訳してtest_translated_wordを作成する
        if(!test_translated_word.contains("Error during translation")){
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ACCOSTS + ".t1");//アプリ開始時の発話、シナリオのpメモリからtargetLanguageを取得し言語切り替えボックスに設定する
        }else{
            Log.v(TAG, "Test_translated_word Is Error Message");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_CONNECTION);//接続が失敗したときの発話
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause()");

        //バックに回ったら発話を中止する.
        VoiceUIManagerUtil.stopSpeech();

        //Scene無効化.
        VoiceUIManagerUtil.disableScene(mVUIManager, ScenarioDefinitions.SCENE_COMMON);

        //VoiceUIListenerの解除.
        VoiceUIManagerUtil.unregisterVoiceUIListener(mVUIManager, mVUIListener);

        //単一Activityの場合はonPauseでアプリを終了する.
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");

        //ホームボタンの検知破棄.
        this.unregisterReceiver(mHomeEventReceiver);

        //インスタンスのごみ掃除.
        mVUIManager = null;
        mVUIListener = null;
    }

    /**
     * VoiceUIListenerクラスからのコールバックを実装する.
     */
    @Override
    public void onScenarioEvent(int event, List<VoiceUIVariable> variables) {
        Log.v(TAG, "onScenarioEvent() : " + event);
        switch (event) {
            case VoiceUIListenerImpl.ACTION_END:
                String function = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.ATTR_FUNCTION);//ここで関数名を格納し、以下のif文で何の関数が呼ばれているのか判定する
                if(ScenarioDefinitions.FUNC_SEND_WORD.equals(function)) {//listenシナリオのsend_word関数
                    final String original_word = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_LVCSR_BASIC);//聞いた単語をString変数に格納

                    if(!(Objects.equals(original_word, ""))) {//正常なテキストなら一連の処理を開始する
                        Log.v(TAG, "Listen Scenario Sent Normal Text");

                        //入力バーにoriginal_wordの内容を表示する
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                inputTextValue.setText(original_word);
                            }
                        });

                        //ここで翻訳先言語を取得し設定する必要がある(プルダウンで言語変更した後に言語で入力した場合に備えて)
                        //翻訳先言語を取得
                        String targetLanguage = spinner_target.getSelectedItem().toString();
                        //翻訳先言語をspeakシナリオの手が届くpメモリに送る
                        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TARGET, targetLanguage);
                        startSpeakScenario(original_word,targetLanguage);//翻訳して画面表示してspeakシナリオを開始させる
                    }else{
                        Log.v(TAG, "Listen Scenario Sent Empty Text");
                    }
                }
                if(ScenarioDefinitions.FUNC_END_APP.equals(function)){//endシナリオのend_app関数
                    Log.v(TAG, "Receive End Voice Command heard");
                    finish();//アプリを終了する
                }
                if(ScenarioDefinitions.FUNC_SET_TARGET.equals(function)) {//targetLanguageシナリオ
                    //翻訳先言語をString変数に格納
                    final String targetLanguage = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_TARGET);
                    Log.v(TAG, "Receive Change Target Language Voice Command Heard. Target Is " + targetLanguage);
                    // R.array.languagesの内容をString配列として取得し、UIスレッド内で翻訳先言語ボックスを入力された言語(の番号を検索しその番号)に切り替える
                    String[] languages = getResources().getStringArray(R.array.languages);
                    runOnUiThread(() -> {
                        spinner_target.setSelection(Arrays.asList(languages).indexOf(targetLanguage));
                    });
                    //翻訳先言語をspeakシナリオの手が届くpメモリに送る
                    int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TARGET, targetLanguage);//この処理を後の関数内でボックスからとったりしてやるとUIスレッドが更新を終える前にとってしまう
                    if (!inputTextValue.getText().toString().trim().equals("")) {//入力バーに単語が入力済みなら
                        //入力テキストを取得しspeakシナリオへ
                        String original_word = inputTextValue.getText().toString().trim();
                        startSpeakScenario(original_word, targetLanguage);
                    } else {//入力されていなければ
                        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ACCOSTS + ".t2");//言語設定変更時の発話
                    }
                }
                if(ScenarioDefinitions.FUNC_INITIAL_TARGET.equals(function)) {//accostsシナリオのt1
                    //翻訳先言語をString変数に格納
                    String targetLanguage = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_TARGET);
                    if(targetLanguage == null){
                        targetLanguage = "英語";
                    }
                    Log.v(TAG, "Receive Change Target Language Voice Command Heard. Target Is " + targetLanguage);
                    // R.array.languagesの内容をString配列として取得し、UIスレッド内で翻訳先言語ボックスを入力された言語(の番号を検索しその番号)に切り替える
                    String[] languages = getResources().getStringArray(R.array.languages);
                    String finalTargetLanguage = targetLanguage;//UIスレッドで使用するためにfinal宣言
                    runOnUiThread(() -> {
                        spinner_target.setSelection(Arrays.asList(languages).indexOf(finalTargetLanguage));
                    });
                    //翻訳先言語をspeakシナリオの手が届くpメモリに送る
                    int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TARGET, targetLanguage);
                }
                break;
            case VoiceUIListenerImpl.RESOLVE_VARIABLE:
            case VoiceUIListenerImpl.ACTION_START:
            case VoiceUIListenerImpl.ACTION_CANCELLED:
            case VoiceUIListenerImpl.ACTION_REJECTED:
            default:
                break;
        }
    }


    //TASK この関数かもっと奥の翻訳する関数でString targetLanguage = spinner_target.getSelectedItem().toString();で翻訳先言語を入力ボックスから取得し使用する
    //とか考えていたが、他の部分との兼ね合いで引数にとる必要が生じた
    /**
     * 翻訳をしてspeakシナリオを開始させる関数
     */
    private void startSpeakScenario(final String original_word,final String targetLanguage){
        if(original_word.length() > max_length || Objects.equals(original_word,null) || Objects.equals(original_word,"")){
            Log.v(TAG, "Original_word Is Wrong");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//original_wordが不正な場合はリターン
        }

        final String translated_word = translateSync(original_word, targetLanguage);//original_wordを英訳したtranslated_wordを作成する
        if(translated_word.contains("Error during translation")){
            Log.v(TAG, "Translated_word Is Error Message");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_CONNECTION);//errorシナリオのconnectionトピックを起動する
            return;//translated_wordがエラーメッセージなのでリターン
        }
        if(translated_word.length() > max_length || Objects.equals(translated_word, null) || Objects.equals(translated_word, "")){
            Log.v(TAG, "Translated_word Is Wrong");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//translated_wordが不正な場合はリターン
        }

        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_ORIGINAL_WORD, original_word);//翻訳前の単語をspeakシナリオの手が届くpメモリに送る
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Set Original_word Failed");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//original_wordのpメモリへの保存が失敗したらリターン
        }
        result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TRANSLATED_WORD, translated_word);//翻訳後の単語をspeakシナリオの手が届くpメモリに送る
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Set Translated_word Failed");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//translated_wordのpメモリへの保存が失敗したらリターン
        }

        result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAK);//speakシナリオを起動する
        //startExplainScenario(translated_word);
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Speak Scenario Failed To Start");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
        }else{
            Log.v(TAG, "Speak Scenario Started");

            //出力バーにtranslated_wordの内容を表示する
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    outputTextValue.setText(translated_word);
                }
            });
        }
    }

    /**
     * 翻訳した結果で、explainシナリオを開始させる関数
     */
    private void startExplainScenario(final String translated_word){
        if(Objects.equals(translated_word,null) || translated_word.length() > max_length){
            Log.v(TAG, "translated_word for explaining Is Wrong");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_EXPLAIN);//errorシナリオのexplainトピックを起動する

            return;//translated_wordが不正な場合はリターン
        }

        final String explanation_words = explainSync(translated_word);//translated_wordをGPTのAPIに送信して、その説明を取得する
        if(explanation_words.contains("Error during explanation request")){
            Log.v(TAG, "explanation_words Is Error Message");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_CONNECTION);//errorシナリオのconnectionトピックを起動する
            return;//explanation_wordsがエラーメッセージなのでリターン
        }
        if(Objects.equals(explanation_words, null) || translated_word.length() > max_length){
            Log.v(TAG, "explanation_words Is Wrong");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_EXPLAIN);//errorシナリオのtranslateトピックを起動する
            return;//translated_wordが不正な場合はリターン
        }

        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_ORIGINAL_WORD, translated_word);//翻訳前の単語をspeakシナリオの手が届くpメモリに送る
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Set translated_word Failed");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_EXPLAIN);//errorシナリオのtranslateトピックを起動する
            return;//original_wordのpメモリへの保存が失敗したらリターン
        }
        result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_EXPLAIN_WORDS, explanation_words);//翻訳後の単語をspeakシナリオの手が届くpメモリに送る
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Set explanation_words Failed");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_EXPLAIN);//errorシナリオのtranslateトピックを起動する

            return;//translated_wordのpメモリへの保存が失敗したらリターン
        }

        result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAK_EXPLANATION);//speakシナリオを起動する
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Speak Explanation Scenario Failed To Start");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_EXPLAIN);//errorシナリオのtranslateトピックを起動する
        }else{
            Log.v(TAG, "Speak Explain Scenario Started");
        }
    }

    //日本語から英語に翻訳
    private String translateSync(String original_word, String target_language) {
        final String[] translatedTextHolder = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        translate(original_word, target_language,result -> {
            translatedTextHolder[0] = result;
            latch.countDown(); // 翻訳処理が終わったサイン
        });

        try {
            latch.await(); // コールバックが終わるまで待機
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return translatedTextHolder[0]; // 翻訳結果を返す
    }

    private void translate(String original_word, String target_language, GPTAPIResultCallback callback) {

        // 翻訳結果の言語を選択
        String targetLanguage = target_language;
        Log.v(TAG, "Start Translate:targetLanguage Is "+targetLanguage);

        // 非同期の関数を呼び出し
        GPTTranslateAPI.translateAsync(original_word, targetLanguage, new GPTTranslateAPI.GPTAPIResultCallback() {
            @Override
            public void onSuccess(String translatedText) {
                // Pass the translated text to the callback
                callback.onResult(translatedText);
            }

            @Override
            public void onError(String errorMessage) {
                // Pass null or an error message to the callback
                callback.onResult(null);
            }
        });
    }

    //翻訳結果の説明
    private String explainSync(String translated_word) {
        final String[] explainTextHolder = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        explain(translated_word, result -> {
            explainTextHolder[0] = result;
            latch.countDown(); // 翻訳処理が終わったサイン
        });

        try {
            latch.await(); // コールバックが終わるまで待機
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return explainTextHolder[0]; // 翻訳結果を返す
    }

    private void explain(String translated_word, GPTAPIResultCallback callback) {

        // 翻訳結果の言語を選択
        String targetLanguage = "en";

        // 非同期の関数を呼び出し
        GPTTranslateAPI.explainResultAsync(translated_word, targetLanguage, new GPTTranslateAPI.GPTAPIResultCallback() {
            @Override
            public void onSuccess(String translatedText) {
                // Pass the translated text to the callback
                callback.onResult(translatedText);
            }

            @Override
            public void onError(String errorMessage) {
                // Pass null or an error message to the callback
                callback.onResult(null);
            }
        });
    }

    public interface GPTAPIResultCallback {
        void onResult(String result);
    }

    /**
     * ホームボタンの押下イベントを受け取るためのBroadcastレシーバークラス.<br>
     * <p/>
     * アプリは必ずホームボタンで終了する.
     */
    private class HomeEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v(TAG, "Receive Home button pressed");
            // ホームボタン押下でアプリ終了する.
            finish();
        }
    }

    /**
     * タイトルバーを設定する.
     */
    private void setupTitleBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
    }

}