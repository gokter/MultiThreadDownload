package com.flyingh.multithreaddownload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpStatus;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	protected static final long THREAD_NUMBER = 5;
	protected static final String TEMP_FILE_EXTENSION = ".tmp";
	protected static final String TAG = "MainActivity";
	private EditText pathEditText;
	private ProgressBar progressBar;
	private TextView textView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		pathEditText = (EditText) findViewById(R.id.path);
		progressBar = (ProgressBar) findViewById(R.id.bar);
		textView = (TextView) findViewById(R.id.current_process);
	}

	public void download(final View view) {
		final String path = pathEditText.getText().toString().trim();
		if (TextUtils.isEmpty(path)) {
			Toast.makeText(this, R.string.path_should_not_be_empty_, Toast.LENGTH_SHORT).show();
			return;
		}
		if (!URLUtil.isNetworkUrl(path)) {
			Toast.makeText(this, "the url is not correct!", Toast.LENGTH_SHORT).show();
			return;
		}
		new Thread(new Runnable() {

			@Override
			public void run() {
				long contentLengthLong = getContentLengthLong(path);
				createSavingFile(getFileName(path), contentLengthLong);
				progressBar.setMax((int) contentLengthLong);
				long size = contentLengthLong / THREAD_NUMBER;
				for (int i = 0; i < THREAD_NUMBER; i++) {
					long start = getStart(i, size, path);
					long end = getEnd(i, size, contentLengthLong);
					Log.i(TAG, "i:" + i + "-->" + start + "-->" + end);
					progressBar.setProgress((int) (progressBar.getProgress() + start - i * size));
					startThreadsToDownload(i, start, end, path);
				}
			}

			private void startThreadsToDownload(final int i, final long start, final long end, final String path) {
				new Thread(new Runnable() {

					@Override
					public void run() {
						try {
							URL url = new URL(path);
							HttpURLConnection connection = (HttpURLConnection) url.openConnection();
							connection.setRequestMethod("GET");
							connection.setConnectTimeout(5000);
							connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
							if (connection.getResponseCode() == HttpStatus.SC_PARTIAL_CONTENT) {
								InputStream is = connection.getInputStream();
								byte[] buffer = new byte[4096];
								int len = 0;
								RandomAccessFile raf = new RandomAccessFile(getSavingPath(getFileName(path)), "rw");
								raf.seek(start);
								long currentPosition = start;
								File file = new File(getTempFilePath(i, path));
								while ((len = is.read(buffer)) != -1) {
									raf.write(buffer, 0, len);
									FileOutputStream fos = new FileOutputStream(file);
									fos.write(String.valueOf(currentPosition += len).getBytes());
									fos.close();
									synchronized (MainActivity.this) {
										progressBar.setProgress(progressBar.getProgress() + len);
										runOnUiThread(new Runnable() {
											@Override
											public void run() {
												textView.setText("Current Progress:" + progressBar.getProgress() * 100
														/ progressBar.getMax() + "%");
												if (progressBar.getProgress() == progressBar.getMax()) {
													Toast.makeText(getApplicationContext(), R.string.download_success_,
															Toast.LENGTH_SHORT).show();
													view.setEnabled(false);
												}
											}
										});
									}
								}
								file.delete();
								Log.i(TAG, "Thread-" + i + " done!");
								raf.close();
								is.close();
							}
						} catch (Exception e) {
							e.printStackTrace();
							handleException(e);
						}
					}
				}).start();
			}

			private long getEnd(int i, long size, long contentLengthLong) {
				return (i == THREAD_NUMBER - 1) ? contentLengthLong : (i + 1) * size - 1;
			}

			private long getStart(int i, long size, String path) {
				File file = new File(getTempFilePath(i, path));
				if (!file.exists()) {
					return i * size;
				}
				try {
					FileInputStream fis = new FileInputStream(file);
					byte[] buffer = new byte[1024];
					int len = fis.read(buffer);
					fis.close();
					if (len == -1) {
						return i * size;
					}
					return Long.parseLong(new String(buffer, 0, len));
				} catch (Exception e) {
					e.printStackTrace();
					handleException(e);
				}
				return 0;
			}

			private String getTempFilePath(int i, String path) {
				return Environment.getExternalStorageDirectory() + "/" + getTempFileName(i, path);
			}

			private String getTempFileName(int i, String path) {
				return getFileName(path) + i + TEMP_FILE_EXTENSION;
			}

			private void createSavingFile(String fileName, long contentLengthLong) {
				try {
					RandomAccessFile raf = new RandomAccessFile(getSavingPath(fileName), "rw");
					raf.setLength(contentLengthLong);
					raf.close();
				} catch (Exception e) {
					e.printStackTrace();
					handleException(e);
				}

			}

			private String getSavingPath(String fileName) {
				return Environment.getExternalStorageDirectory() + "/" + fileName;
			}

			private String getFileName(String path) {
				return path.substring(path.lastIndexOf("/") + 1);
			}

			private long getContentLengthLong(String path) {
				try {
					URL url = new URL(path);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("GET");
					connection.setConnectTimeout(5000);
					if (connection.getResponseCode() == HttpStatus.SC_OK) {
						return connection.getContentLength();
					} else {
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								Toast.makeText(getApplicationContext(), "connect time out!", Toast.LENGTH_SHORT).show();
							}
						});
					}
				} catch (Exception e) {
					handleException(e);
				}
				return 0;
			}

			private void handleException(final Exception e) {
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
					}
				});
			}
		}).start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
