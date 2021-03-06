package com.dasu.update;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static com.dasu.update.ConstValue.LOG_TAG;

/**
 * 只负责后台去下载 APK 的工作
 * Created by suxq on 2017/4/7.
 */

class UpdateAsyncTask extends AsyncTask<String, Integer, Boolean> {

    private static final String TAG = "UpdateAsyncTask";
    private OnUpdateListener mUpdateListener;
    private String mApkUrl;
    private String mApkFilePath;
    private int mLastProgress = -1;

    public UpdateAsyncTask(String apkUrl, String filePath,
                           OnUpdateListener listener) {
        mApkUrl = apkUrl;
        mApkFilePath = filePath;
        mUpdateListener = listener;
    }

    @Override
    protected Boolean doInBackground(String... params) {
        if (UpdateConfig.isDebugLogEnable) {
            Log.d(LOG_TAG, TAG + ": begin download apk, url = " + mApkUrl);
        }
        BufferedInputStream bis = null;
        RandomAccessFile randomAccessFile = null;
        //使用URLConnection下载apk文件，支持下载 https 链接
        URLConnection conn;
        try {
            String url = mApkUrl;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            //初始化https相关配置
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new MyTrustManager()}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new MyHostnameVerifier());
            //配置请求的相关参数
            URL downloadUrl = new URL(url);
            conn = downloadUrl.openConnection();
            conn.setAllowUserInteraction(true);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("Connection", "Keep-Alive");
            //设置下载保存的临时文件
            File apkFile = new File(mApkFilePath);
            File parent = new File(apkFile.getParent());
            File tempFile = new File(apkFile.getParent(), "apk_temp");
            if (!parent.exists()) {
                parent.mkdirs();
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (apkFile.exists()) {
                apkFile.delete();
            }
            //开始下载文件
            byte[] buffer = new byte[1024];
            bis = new BufferedInputStream(conn.getInputStream());
            randomAccessFile = new RandomAccessFile(tempFile, "rwd");
            int fileSize = conn.getContentLength();
            int downloadLength = 0;
            int byteRead;
            while ((byteRead = bis.read(buffer, 0, 1024)) != -1) {
                if (isCancelled()) {
                    return false;
                }
                randomAccessFile.write(buffer, 0, byteRead);
                downloadLength += byteRead;
                int progress = (int) ((1.0f * downloadLength / fileSize) * 100);
                publishProgress(progress);
            }
            if (!tempFile.renameTo(apkFile)) {
                copyFile(tempFile, apkFile, true);
            }
            return true;
        } catch (Exception e) {
            Log.w(LOG_TAG, TAG + ": download error, cause " + e.getMessage() + ", apk url =" + mApkUrl);
            e.printStackTrace();
        } finally {
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
                if (bis != null) {
                    bis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static boolean copyFile(File sourceFile, File destFile, boolean isDeleteSource) {
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            int byteRead = 0;
            if (sourceFile.exists()) {
                is = new FileInputStream(sourceFile);
                os = new FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
                while ((byteRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, byteRead);
                }
                if (isDeleteSource) {
                    sourceFile.delete();
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            if (destFile.exists()) {
                destFile.delete();
            }
            return false;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (mUpdateListener != null) {
            int nowProgress = values[0];
            if (mLastProgress < nowProgress) {
                //values[0]:当前下载的进度，取值 0~100
                mUpdateListener.onDownloading(values[0]);
                mLastProgress = nowProgress;
            }
        }
    }

    @Override
    protected void onPostExecute(Boolean isSucceed) {
        if (mUpdateListener != null) {
            mUpdateListener.onDownloadFinish(isSucceed, mApkFilePath);
            if (UpdateConfig.isDebugLogEnable) {
                Log.d(LOG_TAG, TAG + " download finish, isSuccess = " + isSucceed + ", apkFilePath=" + mApkFilePath);
            }
        }
    }



    private class MyHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String hostname, SSLSession session) {
            //检查受信任的域名列表时，这里也什么都没做，默认通过
            return true;
        }
    }

    private class MyTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            //当检查不受信任的证书书，这里直接什么都没做
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
