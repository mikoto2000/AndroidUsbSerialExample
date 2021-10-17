package dev.mikoto2000.android.usebserialsend;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Android の USB シリアル通信例。
 *
 * See: https://qiita.com/cmadayo/items/849230a59dab4b55c419
 *      https://qiita.com/aftercider/items/81edf35993c2df3de353
 *
 */
public class MainActivity extends AppCompatActivity {

    private SerialInputOutputManager mSerialIoManager;
    private SerialInputOutputManager.Listener mListener;
    private UsbSerialPort mPort;
    private UsbDeviceConnection usbConnection;
    private Handler mainLooper;

    private TextView mText;

    // 任意のメソッド。恐らくonCreate等のライフサイクルで実施することになる。
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.mText = this.findViewById(R.id.textview);

        this.mText.setText("");
        this.mText.setText("initialized...");

        mainLooper = new Handler(Looper.getMainLooper());

        // Find all available drivers from attached devices.
        // Android標準のAPIを使用して、USBサービスのマネージャを作成します
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // 以下はusb-serial-for-androidのAPIです。使用可能なUSBシリアル通信のドライバーを取得します。
        // Proberというのは後述でも出てきますが、使用するケーブルのベンダIDと製品IDのマッピングテーブルです
        // availableDriversにはProverに登録されているかつ、接続されているUSBシリアル変換ケーブルがあるときに値が入ります
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            mText.append("drivers empty!");
            return;
        }

        // Open a connection to the first available driver.
        // 以下、ReadMeのサンプルでは、get(0)でとりあえず最初のドライバーを選択しています。
        UsbSerialDriver driver = availableDrivers.get(0);

        // シリアルポート作成処理
        mPort = driver.getPorts().get(0); // Most devices have just one port (port 0)
        usbConnection = manager.openDevice(driver.getDevice());

        // コールバック用のリスナを生成
        mListener = new SerialInputOutputManager.Listener() {
            // データ受信時に呼ばれるコールバックメソッド
            @Override
            public void onNewData(byte[] data) {
                mainLooper.post(() -> {
                    mText.append(new String(data));
                });
            }

            // 何かしらのエラーを検知したときに呼ばれるコールバックメソッド
            // 例えばケーブルが抜けた、とか
            @Override
            public void onRunError(Exception e) {
                // UIスレッドに処理をコールバック
                mainLooper.post(() -> {
                    stopSerial();
                });
            }
        };
        this.startSerial();

        final Handler handler = new Handler();
        final Runnable r = new Runnable() {
            int count = 0;
            @Override
            public void run() {
                if (mPort != null) {
                    try {
                        final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                        final Date date = new Date(System.currentTimeMillis());
                        final String sendText = df.format(date) + "\n";
                        mPort.write(sendText.getBytes(StandardCharsets.UTF_8), 1000);
                        mText.append(sendText);
                    } catch (Exception e) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        e.printStackTrace(pw);
                        mText.append(sw.toString());
                    }
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(r);
    }

    // 任意のメソッド。恐らくonDestroy等のライフサイクルで実施することになる。
    @Override
    protected void onDestroy() {
        // onDestroyで必要な処理は省略
        if (mSerialIoManager != null) {
            this.stopSerial();
        }

        super.onDestroy();
    }

    // シリアル通信開始用のメソッド
    private void startSerial() {
        if (mPort != null) {
            // シリアル通信マネージャと、シリアルポート、イベント受信時のコールバックを紐づける
            try {
                mPort.open(usbConnection);
                mPort.setParameters(115200, 8, 1, UsbSerialPort.PARITY_NONE);
                mSerialIoManager = new SerialInputOutputManager(mPort, mListener);
                mSerialIoManager.start();
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                mText.append(sw.toString());
            }
            mText.append("done.\n");
        } else {
            // 適当にエラーハンドリング
            mText.append("シリアルポートが見つかりませんでした。");
        }
    }

    // シリアル通信停止用のメソッド
    private void stopSerial() {
        if (mSerialIoManager != null) {
            // シリアル通信マネージャを停止
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
        if (mPort != null) {
            try {
                // シリアルポートをクローズする
                mPort.close();
            } catch (IOException e) {
                // 適当にエラーハンドリング
            }
            mPort = null;
        }
    }
}