package com.example.liyang.udpboardcastserver;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static String ip; //服务端ip
    private static int BROADCAST_PORT = 1234;
    private static int PORT = 4444;
    private static String BROADCAST_IP = "224.0.0.1";
    InetAddress inetAddress = null;
    /*发送广播端的socket*/
    MulticastSocket multicastSocket = null;
    /*发送广播的按钮*/
    private Button sendUDPBrocast;
    private volatile boolean isRuning = true;
    public TextView text1;
    public EditText input;
    public Button btn;
    public StringBuffer strContent = new StringBuffer();
    private UDPBoardcastThread boardcastThread = null;
    private List<Socket> mList = new ArrayList<Socket>();
    private ExecutorService mExecutorService = null;//thread pool
    private ServerSocket server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initData();
        initUI();
        //开始广播
        boardcastThread = new UDPBoardcastThread();
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    server = new ServerSocket(PORT);
                    mExecutorService = Executors.newCachedThreadPool();
                    Socket client = null;
                    while (true) {
                        client = server.accept();
                        //把客户端放入客户端集合中
                        if (!connectOrNot(client)) {
                            mList.add(client);

                            Bundle bundle = new Bundle();
                            bundle.putString("content", "当前连接数" + mList.size());
                            Message msg = new Message();
                            msg.setData(bundle);
                            msg.what = 0x1238;
                            mhandler.sendMessage(msg);

                        }
                        mExecutorService.execute(new Service(client)); //start a new thread to handle the connection
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void initData() {
        try {
            ip = getAddressIP();
            inetAddress = InetAddress.getByName(BROADCAST_IP);//多点广播地址组
            multicastSocket = new MulticastSocket(BROADCAST_PORT);//多点广播套接字
            multicastSocket.setTimeToLive(1);
            multicastSocket.joinGroup(inetAddress);//加入广播地址组

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initUI() {
        text1 = (TextView) findViewById(R.id.txt_show);
        input = (EditText) findViewById(R.id.et_input);
        btn = (Button) findViewById(R.id.btn_send);
    }


    /**
     * 1.获取本机正在使用wifi IP地址
     */
    public String getAddressIP() {
        WifiManager wifimanage = (WifiManager) getSystemService(Context.WIFI_SERVICE);//获取WifiManager
        //检查wifi是否开启
        if (!wifimanage.isWifiEnabled()) {
            wifimanage.setWifiEnabled(true);
        }
        WifiInfo wifiinfo = wifimanage.getConnectionInfo();

        String ip = intToIp(wifiinfo.getIpAddress());
        return ip;
    }

    public String intToIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
    }

    private Handler mhandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0x1237:
                    //服务器连接失败提示
                    Toast.makeText(MainActivity.this, "服务器连接失败", Toast.LENGTH_SHORT).show();
                    break;
                case 0x1238:
                    //服务器端拦截发送的消息
                    strContent.append(msg.getData().getString("content") + "\n");
                    text1.setText(strContent.toString());
                    break;
            }
        }
    };

    /**
     * UDP广播 建立连接使用
     */
    public class UDPBoardcastThread extends Thread {
        public UDPBoardcastThread() {
            this.start();
        }

        @Override
        public void run() {
            DatagramPacket dataPacket = null;
            //将本机的IP（这里可以写动态获取的IP）地址放到数据包里，其实server端接收到数据包后也能获取到发包方的IP的
            byte[] data = ip.getBytes();
            dataPacket = new DatagramPacket(data, data.length, inetAddress, BROADCAST_PORT);
            while (true) {
                if (isRuning) {
                    try {
                        multicastSocket.send(dataPacket);
                        Thread.sleep(3000);
                        System.out.println("再次发送ip地址广播:.....");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    public class Server implements Runnable {
        @Override
        public void run() {

            DatagramSocket serverSocket = null;
            InetAddress serverAddress = null;
            while (true) {
                try {
                    serverAddress = InetAddress.getByName(ip);
                    serverSocket = new DatagramSocket(PORT, serverAddress);//服务器端套接字

                    byte[] buf = new byte[255];
                    System.out.println("等待抓取数据中:.....");
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    serverSocket.receive(packet);
                    if (!TextUtils.isEmpty(new String(packet.getData()))) {
                        Bundle bundle = new Bundle();
                        bundle.putString("content", new String(packet.getData()) + "[" + packet.getAddress().getHostAddress() + "]");
                        Message msg = new Message();
                        msg.setData(bundle);
                        msg.what = 0x1238;
                        mhandler.sendMessage(msg);


                        //收到消息后的回执
                        String success = "我已经收到此消息";
                        buf = success.getBytes();
                        DatagramPacket reply = new DatagramPacket(buf, buf.length,
                                packet.getAddress(), PORT);
                        serverSocket.send(reply);
                        Log.e("msg", "send suceess !!!!");
                        serverSocket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    mhandler.sendEmptyMessage(0x1237);
                }
            }
//            try {
//                ServerSocket ss = new ServerSocket(PORT);
//                while (true) {
//                    // 等待客户端连接
//                    Socket s = ss.accept();
//                    BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
//                    //注意第二个参数据为true将会自动flush，否则需要需要手动操作output.flush()
//                    PrintWriter output = new PrintWriter(s.getOutputStream(), true);
//                    String message = input.readLine();
//                    if (!TextUtils.isEmpty(message)) {
//                        Bundle bundle = new Bundle();
//                        bundle.putString("content", message + "[" + s.getInetAddress() + "]");
//                        Message msg = new Message();
//                        msg.setData(bundle);
//                        msg.what = 0x1238;
//                        mhandler.sendMessage(msg);
//                    }
//                    s.close();
//                    ss.close();
//                }
//            } catch (UnknownHostException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            } finally {
//                mhandler.sendEmptyMessage(0x1237);
//            }
        }
    }


    class Service implements Runnable {
        private Socket socket;
        private BufferedReader in = null;
        private String msg = "";

        public Service(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void run() {
            try {
                while (true) {
                    if ((msg = in.readLine()) != null) {
                        //当客户端发送的信息为：exit时，关闭连接
                        if (msg.equals("exit")) {
                            mList.remove(socket);
                            in.close();
                            msg = "user:" + socket.getInetAddress()
                                    + "exit total:" + mList.size();
                            socket.close();
                            this.sendmsg(socket);

                            break;
                            //接收客户端发过来的信息msg，然后发送给客户端。
                        } else {
                            msg = socket.getInetAddress() + "（客户发送):" + msg;
                            Bundle bundle = new Bundle();
                            bundle.putString("content", msg);
                            Message msg = new Message();
                            msg.setData(bundle);
                            msg.what = 0x1238;
                            mhandler.sendMessage(msg);


                            this.sendmsg(socket);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * 循环遍历客户端集合，给每个客户端都发送信息。
         */
        public void sendmsg(Socket socket) {
            PrintWriter pout = null;
            try {
                if (!connectOrNot(socket)) {
                    pout = new PrintWriter(new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream())), true);
                    pout.println("您已经建立连接\n" + msg);
                } else {
                    pout = new PrintWriter(new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream())), true);
                    pout.println("服务器返回的随机数" + Math.random());
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private boolean connectOrNot(Socket socket) {
        int num = mList.size();
        for (int index = 0; index < num; index++) {
            Socket mSocket = mList.get(index);
            if (mSocket.getInetAddress().getHostAddress().equals(socket.getInetAddress().getHostAddress())) {
                return true;

            }
        }
        return false;

    }
}
