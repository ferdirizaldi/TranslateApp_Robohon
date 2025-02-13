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
import android.util.Log;

import android.text.TextWatcher;
import android.text.Editable;

import java.util.List;
import java.util.Locale;//追加1/17 multilingualからのコピペ
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.Arrays;


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
    private String inputLanguage;//翻訳前言語をアクティビティ側でも保存する　シナリオ側ではpメモリのjp.co.sharp.translate.app.inputLanguageに保存される
    private Spinner inputSpinner;//いろんな関数で触るためここで宣言
    private String targetLanguage;//翻訳先言語をアクティビティ側でも保存する　シナリオ側ではpメモリのjp.co.sharp.translate.app.targetLanguageに保存される
    private Spinner targetSpinner;//いろんな関数で触るためここで宣言
    private EditText inputTextValue;
    private TextView outputTextValue;
    private Button resultExpainButton;//説明ボタン
    private final int max_length = 100;//翻訳前後の文の長さの許容限界

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

        // 翻訳ボタン表示
        Button voiceTranslateButton = (Button) findViewById(R.id.voice_translate_button);
        voiceTranslateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTextProcessing();
            }
        });
        voiceTranslateButton.setEnabled(false);//最初は使用不可

        // 説明ボタン表示
        resultExpainButton = (Button) findViewById(R.id.result_explain_button);
        resultExpainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startExplainScenario(outputTextValue.getText().toString());
            }
        });
        resultExpainButton.setEnabled(false);//最初は使用不可

        // 終了ボタン取得
        Button finishButton = (Button) findViewById(R.id.finish_app_button);

        // 終了ボタンの処理
        finishButton.setOnClickListener(view -> {
            // Finish the current activity
            finish();
        });

        //テキストボックスを取得
        inputTextValue = (EditText) findViewById(R.id.input_text_value);
        outputTextValue = (TextView) findViewById(R.id.output_text_value);

        inputTextValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // テキスト変更前に呼び出されます
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // テキスト変更中に呼び出されます
            }

            @Override
            public void afterTextChanged(Editable s) {
                // テキスト変更後に呼び出されます
                String newText = s.toString();
                // ここで新しいテキストの内容を検知し、必要な処理を行います
                if(Objects.equals(newText, "")){//テキストボックスが空なら
                    voiceTranslateButton.setEnabled(false);//使用不可にする
                }else{//テキストボックスに何か入力されていれば
                    voiceTranslateButton.setEnabled(true);//使用可能にする
                }
            }
        });

        //入力側の言語切り替えボックスを作成し、対応言語一覧をセット
        inputSpinner = (Spinner) findViewById(R.id.spinner_input);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.inputLanguages, R.layout.spinner_layout);
        adapter.setDropDownViewResource(R.layout.spinner_layout);
        inputSpinner.setAdapter(adapter);
        // リスナーを登録
        inputSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            //　アイテムが選択された時
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view, int position, long id) {
                Spinner spinner = (Spinner)parent;
                String item = (String)spinner.getSelectedItem();
                //翻訳前言語をjavaで使えるよう格納する
                inputLanguage = item;
                //翻訳前言語をspeakシナリオの手が届くpメモリに送る
                int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_INPUT, item);
                //認識言語を変更する
                if(Objects.equals(inputLanguage, "日本語")) {
                    VoiceUIManagerUtil.setAsr(mVUIManager, Locale.JAPAN);//認識言語の変更
                }
                if(Objects.equals(inputLanguage, "英語")) {
                    VoiceUIManagerUtil.setAsr(mVUIManager, Locale.US);//認識言語の変更
                }
                if(Objects.equals(inputLanguage, "中国語")) {
                    VoiceUIManagerUtil.setAsr(mVUIManager, Locale.CHINA);//認識言語の変更
                }
                if(Objects.equals(inputLanguage, "韓国語")) {
                    VoiceUIManagerUtil.setAsr(mVUIManager, Locale.KOREA);//認識言語の変更
                }
                //入出力バーを空にする
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        inputTextValue.setText("");
                        outputTextValue.setText("");
                        resultExpainButton.setEnabled(false);//出力バーが空になったので説明ボタンを使用不可にする
                    }
                });
            }

            //　アイテムが選択されなかった
            public void onNothingSelected(AdapterView<?> parent) {
                //何もしない
            }
        });


        //出力側の言語切り替えボックスを作成し、対応言語一覧をセット
        targetSpinner = (Spinner) findViewById(R.id.spinner_target);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this, R.array.targetLanguages, R.layout.spinner_layout);
        adapter2.setDropDownViewResource(R.layout.spinner_layout);
        targetSpinner.setAdapter(adapter2);
        // リスナーを登録
        targetSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            //　アイテムが選択された時
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view, int position, long id) {
                Spinner spinner = (Spinner)parent;
                String item = (String)spinner.getSelectedItem();
                //翻訳先言語をjavaで使えるよう格納する
                targetLanguage = item;
                //翻訳先言語をspeakシナリオの手が届くpメモリに送る
                int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TARGET, item);
                //出力バーを空にする
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        outputTextValue.setText("");
                        resultExpainButton.setEnabled(false);//出力バーが空になったので説明ボタンを使用不可にする
                    }
                });
            }

            //　アイテムが選択されなかった
            public void onNothingSelected(AdapterView<?> parent) {
                //何もしない
            }
        });

    }

    /**
     * Handle the text processing when the button is clicked.
     */
    private void handleTextProcessing() {
        //入力テキストを取得
        String original_word = inputTextValue.getText().toString().trim();

        //speakシナリオを開始させる
        startSpeakScenario(original_word);
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

        //アプリ開始時にシナリオのpメモリからtargetLanguageを取得して言語の初期設定を行い、そのまま翻訳言語設定時の発話まで行う
        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ACCOSTS + ".t1");

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

        //デフォルトの言語設定に戻す
        Locale locale = Locale.getDefault();
        VoiceUIManagerUtil.setAsr(mVUIManager, locale);
        VoiceUIManagerUtil.setTts(mVUIManager, locale);

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

                        startSpeakScenario(original_word);//翻訳して画面表示してspeakシナリオを開始させる
                    }else{
                        Log.v(TAG, "Listen Scenario Sent Empty Text");
                    }
                }

                if(ScenarioDefinitions.FUNC_EXPLAIN_WORD.equals(function)){
                    String translated_word = outputTextValue.getText().toString();
                    startExplainScenario(translated_word);
                }

                if(ScenarioDefinitions.FUNC_END_APP.equals(function)){//endシナリオのend_app関数
                    Log.v(TAG, "Receive End Voice Command heard");
                    finish();//アプリを終了する
                }

                if(ScenarioDefinitions.FUNC_SET_INPUT.equals(function)) {//inputLanguageシナリオから呼ばれ、翻訳前言語を設定する
                    //翻訳前言語をString変数に格納
                    inputLanguage = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_INPUT);
                    Log.v(TAG,"Set InputLanguage:" + inputLanguage);

                    // R.array.languagesの内容をString配列として取得し、UIスレッド内で翻訳先言語ボックスを入力された言語(の番号を検索しその番号)に切り替える
                    String[] languages = getResources().getStringArray(R.array.inputLanguages);
                    String finalInputLanguage = inputLanguage;//UIスレッドで使用するためにfinal宣言
                    runOnUiThread(() -> {
                        inputSpinner.setSelection(Arrays.asList(languages).indexOf(finalInputLanguage));
                    });

                    //認識言語を変更する
                    if(Objects.equals(inputLanguage, "日本語")) {
                        VoiceUIManagerUtil.setAsr(mVUIManager, Locale.JAPAN);//認識言語の変更
                    }
                    if(Objects.equals(inputLanguage, "英語")) {
                        VoiceUIManagerUtil.setAsr(mVUIManager, Locale.US);//認識言語の変更
                    }
                    if(Objects.equals(inputLanguage, "中国語")) {
                        VoiceUIManagerUtil.setAsr(mVUIManager, Locale.CHINA);//認識言語の変更
                    }
                    if(Objects.equals(inputLanguage, "韓国語")) {
                        VoiceUIManagerUtil.setAsr(mVUIManager, Locale.KOREA);//認識言語の変更
                    }
                    //入出力バーを空にする
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            inputTextValue.setText("");
                            outputTextValue.setText("");
                            resultExpainButton.setEnabled(false);//出力バーが空になったので説明ボタンを使用不可にする
                        }
                    });

                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ACCOSTS + ".t2");//翻訳先言語の設定へ
                }

                if(ScenarioDefinitions.FUNC_SET_TARGET.equals(function)) {//accostsシナリオやtargetLanguageシナリオから呼ばれ、翻訳先言語を設定する
                    //翻訳先言語をString変数に格納
                    targetLanguage = VoiceUIVariableUtil.getVariableData(variables, ScenarioDefinitions.KEY_TARGET);
                    Log.v(TAG,"Set TargetLanguage:" + targetLanguage);
                    if(Objects.equals(targetLanguage, "null")){//設定が無いようならデフォルトの英語にする
                        targetLanguage = "英語";
                        //翻訳先言語をspeakシナリオの手が届くpメモリに送る targetLanguageシナリオでセットメモリーできるならこの処理はif文の中でのみすればよい
                        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_TARGET, targetLanguage);
                    }

                    // R.array.languagesの内容をString配列として取得し、UIスレッド内で翻訳先言語ボックスを入力された言語(の番号を検索しその番号)に切り替える
                    String[] languages = getResources().getStringArray(R.array.targetLanguages);
                    String finalTargetLanguage = targetLanguage;//UIスレッドで使用するためにfinal宣言
                    runOnUiThread(() -> {
                        targetSpinner.setSelection(Arrays.asList(languages).indexOf(finalTargetLanguage));
                    });

                    if (!inputTextValue.getText().toString().trim().equals("")) {//入力バーに単語が入力済みなら
                        //入力テキストを取得しspeakシナリオへ
                        String original_word = inputTextValue.getText().toString().trim();
                        startSpeakScenario(original_word);
                    } else {//入力されていなければ
                        VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ACCOSTS + ".t3");//翻訳言語設定時の発話
                    }
                }

                if(ScenarioDefinitions.FUNC_SPEAKS_RELAY1.equals(function)){//speaksシナリオの中継　その1
                    //翻訳前の言語を発話するための発話言語の変更
                    if(Objects.equals(inputLanguage, "日本語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.JAPAN);
                    }
                    if(Objects.equals(inputLanguage, "英語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.US);
                    }
                    if(Objects.equals(inputLanguage, "中国語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.CHINA);
                    }
                    if(Objects.equals(inputLanguage, "韓国語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.KOREA);
                    }
                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAKS + ".t2");
                }
                if(ScenarioDefinitions.FUNC_SPEAKS_RELAY2.equals(function)){//speaksシナリオの中継　その2
                    VoiceUIManagerUtil.setTts(mVUIManager, Locale.JAPAN);//発話言語の変更
                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAKS + ".t3");
                }
                if(ScenarioDefinitions.FUNC_SPEAKS_RELAY3.equals(function)) {//speaksシナリオの中継　その3
                    if(Objects.equals(targetLanguage, "日本語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.JAPAN);//発話言語の変更
                    }
                    if(Objects.equals(targetLanguage, "英語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.US);//発話言語の変更
                    }
                    if(Objects.equals(targetLanguage, "中国語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.CHINA);//発話言語の変更
                    }
                    if(Objects.equals(targetLanguage, "韓国語")) {
                        VoiceUIManagerUtil.setTts(mVUIManager, Locale.KOREA);//発話言語の変更
                    }
                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAKS + ".t4");
                }
                if(ScenarioDefinitions.FUNC_SPEAKS_RELAY4.equals(function)) {//speaksシナリオの中継　その4
                    VoiceUIManagerUtil.setTts(mVUIManager, Locale.JAPAN);//発話言語の変更
                    VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAKS + ".t5");
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

    /**
     * 翻訳をしてspeakシナリオを開始させる関数
     */
    private void startSpeakScenario(final String original_word){
        if(original_word.length() > max_length || Objects.equals(original_word,"")){
            Log.v(TAG, "Original_word Is Wrong");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//original_wordが不正な場合はリターン
        }

        final String translated_word = translateSync(original_word);//original_wordを英訳したtranslated_wordを作成する
        if(translated_word.contains("Error during translation")){
            Log.v(TAG, "Translated_word Is Error Message");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_CONNECTION);//errorシナリオのconnectionトピックを起動する
            return;//translated_wordがエラーメッセージなのでリターン
        }
        if(translated_word.length() > max_length || Objects.equals(translated_word, "")){
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

        result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAKS + ".t1");//speakシナリオを起動する
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
                    resultExpainButton.setEnabled(true);//出力バーに文字が表示されたので説明ボタンを使用可能にする
                }
            });
        }
    }

    /**
     * 翻訳した結果で、explainシナリオを開始させる関数
     */
    private void startExplainScenario(final String translated_word){
        if(Objects.equals(translated_word,null) || translated_word.length() > max_length || Objects.equals(translated_word,"")){
            Log.v(TAG, "translated_word for explaining Is Wrong");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_EXPLAIN);//errorシナリオのexplainトピックを起動する
            return;//translated_wordが不正な場合はリターン
        }

        final String explanation_words = explainSync(translated_word);//translated_wordをGPTのAPIに送信して、その説明を取得する
        if(explanation_words.contains("Error during explanation request") || Objects.equals(explanation_words,"")){
            Log.v(TAG, "explanation_words Is Error Message");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_CONNECTION);//errorシナリオのconnectionトピックを起動する
            return;//explanation_wordsが不正なのでリターン
        }
        System.out.println(explanation_words);
        int result = VoiceUIManagerUtil.setMemory(mVUIManager, ScenarioDefinitions.MEM_P_EXPLAIN_WORDS, explanation_words);//説明文章
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Explanation word is error");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_TRANSLATE);//errorシナリオのtranslateトピックを起動する
            return;//translated_wordのpメモリへの保存が失敗したらリターン
        }

        result = VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_SPEAK_EXPLANATION);//speak_explanationシナリオを起動する
        if(Objects.equals(result,VoiceUIManager.VOICEUI_ERROR)){
            Log.v(TAG, "Speak Explanation Scenario Failed To Start");
            VoiceUIManagerUtil.startSpeech(mVUIManager, ScenarioDefinitions.ACC_ERROR_EXPLAIN);//errorシナリオのtranslateトピックを起動する
        }else{
            Log.v(TAG, "Speak Explain Scenario Started");
        }
    }

    //日本語から英語に翻訳
    private String translateSync(String original_word) {
        final String[] translatedTextHolder = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        translate(original_word, result -> {
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

    private void translate(String original_word, GPTAPIResultCallback callback) {

        // 非同期の関数を呼び出し
        GPTTranslateAPI.translateAsync(original_word, inputLanguage, targetLanguage, new GPTTranslateAPI.GPTAPIResultCallback() {
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

        // 非同期の関数を呼び出し
        GPTTranslateAPI.explainResultAsync(translated_word, inputLanguage, targetLanguage, new GPTTranslateAPI.GPTAPIResultCallback() {
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