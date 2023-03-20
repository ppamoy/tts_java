package io.github.jsbxyyx.tts;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * @author jsbxyyx
 */
public class TtsClient extends WebSocketClient {

    private static final String base_dir = System.getProperty("tts.output.dir", System.getProperty("user.home"));
    private static String req_id = UUID.randomUUID().toString().replace("-", "").toUpperCase();
    private static final byte[] sep = "Path:audio\r\n".getBytes(StandardCharsets.UTF_8);

    private final CountDownLatch latch = new CountDownLatch(1);
    private String x_time;
    private FileOutputStream output;
    private String ssml;

    private JTextArea log;

    public TtsClient setLog(JTextArea log) {
        this.log = log;
        return this;
    }

    public TtsClient() throws Exception {
        super(new URI("wss://eastus.api.speech.microsoft.com/cognitiveservices/websocket/v1?TrafficType=AzureDemo&Authorization=bearer%20undefined&X-ConnectionId="
                        + req_id),
                new HashMap() {
                    {
                        put("origin", "https://azure.microsoft.com");
                        put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.5060.66 Safari/537.36 Edg/103.0.1264.44");
                    }
                });
    }

    public void createContent(String ssml, TtsCallback callback) throws Exception {
        this.ssml = ssml;
        File file = new File(base_dir + "/tts-" + getFilename() + ".mp3");
        output = new FileOutputStream(file, true);
        connect();
        latch.await();
        close();
        log(":: file :: \r\n" + file.getAbsolutePath() + "\r\n");
        if (callback != null) {
            callback.call(file);
        }
        reset();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        payload_1();
        payload_2();
        payload_3();
    }

    @Override
    public void onMessage(String message) {
        log(":: onMessage ::\r\n" + message + "\r\n");
        if (message.indexOf("Path:turn.end") > 0) {
            latch.countDown();
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        byte[] rawData = bytes.array();
        log(":: onMessage blob :: " + rawData.length + "\r\n");
        int index = indexOf(rawData, sep);
        byte[] data = new byte[rawData.length - (index + sep.length)];
        System.arraycopy(rawData, index + sep.length, data, 0, data.length);
        try {
            output.write(data);
            output.flush();
        } catch (IOException e) {
            log(":: onMessage ::\r\n" + getStackTraceAsString(e) + "\r\n");
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log(":: onClose ::\r\n" + code + " :: " + reason + " :: " + remote + "\r\n");
    }

    @Override
    public void onError(Exception ex) {
        log(":: onError ::\r\n" + getStackTraceAsString(ex) + "\r\n");
    }

    void payload_1() {
        String payload_1 = "{\"context\":{\"system\":{\"name\":\"SpeechSDK\",\"version\":\"1.12.1-rc.1\",\"build\":\"JavaScript\",\"lang\":\"JavaScript\",\"os\":{\"platform\":\"Browser/Linux x86_64\",\"name\":\"Mozilla/5.0 (X11; Linux x86_64; rv:78.0) Gecko/20100101 Firefox/78.0\",\"version\":\"5.0 (X11)\"}}}}";
        String message_1 = "Path : speech.config\r\nX-RequestId: " + req_id + "\r\nX-Timestamp: " +
                getXTime() + "\r\nContent-Type: application/json\r\n\r\n" + payload_1;
        send(message_1);
        log(":: payload_1 :: \r\n" + message_1 + "\r\n");
    }

    void payload_2() {
        String payload_2 = "{\"synthesis\":{\"audio\":{\"metadataOptions\":{\"sentenceBoundaryEnabled\":false,\"wordBoundaryEnabled\":false},\"outputFormat\":\"audio-16khz-32kbitrate-mono-mp3\"}}}";
        String message_2 = "Path : synthesis.context\r\nX-RequestId: " + req_id + "\r\nX-Timestamp: " +
                getXTime() + "\r\nContent-Type: application/json\r\n\r\n" + payload_2;
        send(message_2);
        log(":: payload_2 :: \r\n" + message_2 + "\r\n");
    }

    void payload_3() {
        if (isNullOrEmpty(ssml)) {
            this.ssml = "<speak xmlns=\"http://www.w3.org/2001/10/synthesis\" xmlns:mstts=\"http://www.w3.org/2001/mstts\" xmlns:emo=\"http://www.w3.org/2009/10/emotionml\" version=\"1.0\" xml:lang=\"en-US\">\n" +
                    "<voice name=\"zh-CN-XiaoxiaoNeural\">\n" +
                    "<prosody rate=\"0%\" pitch=\"0%\">\n" +
                    "Java编程思想 第4版\n" +
                    "</prosody>\n" +
                    "</voice>\n" +
                    "</speak>";
        }
        String payload_3 = ssml;
        String message_3 = "Path: ssml\r\nX-RequestId: " + req_id + "\r\nX-Timestamp: " +
                getXTime() + "\r\nContent-Type: application/ssml+xml\r\n\r\n" + payload_3;
        send(message_3);
        log(":: payload_3 :: \r\n" + message_3 + "\r\n");
    }

    String getXTime() {
        if (x_time != null) {
            return x_time;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int millisecond = cal.get(Calendar.MILLISECOND);

        x_time = String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03dZ", year, month, day, hour, minute, second, millisecond);
        log(":: time ::\r\n" + x_time + "\r\n");
        return x_time;
    }

    String getFilename() {
        String xTime = getXTime();
        return xTime.replace(":", "-").replace(".", "-");
    }

    void log(String str) {
        if (log != null) {
            if (log.getText().length() > 5000) {
                log.setText(log.getText().substring(5000));
            }
            log.append(str);
            log.setCaretPosition(log.getDocument().getLength());
        }
        System.out.println(str);
    }

    void reset() {
        req_id = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        x_time = null;
    }

    static int indexOf(byte[] array, byte[] target) {
        if (array == null) throw new NullPointerException("array");
        if (target == null) throw new NullPointerException("target");
        if (target.length == 0) {
            return 0;
        }
        outer:
        for (int i = 0; i < array.length - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    static String getStackTraceAsString(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

}
