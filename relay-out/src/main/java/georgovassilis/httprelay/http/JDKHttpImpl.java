package georgovassilis.httprelay.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

/**
 * HTTP implementation using JDK classes
 * 
 * @author george georgovassilis
 *
 */
public class JDKHttpImpl implements Http {

	protected Proxy proxy = null;
	protected int connectionTimeoutMs = 30000;

	public JDKHttpImpl(Proxy proxy) {
		this.proxy = proxy;
	}

	protected boolean doesPost(RequestTask request) {
		return "PUT".equalsIgnoreCase(request.getMethod()) || "POST".equalsIgnoreCase(request.getMethod());
	}

	protected HttpURLConnection openConnection(RequestTask request) throws IOException {
		URL url = new URL(request.getUrl());
		HttpURLConnection conn = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
		conn.setRequestMethod(request.getMethod());
		for (String header : request.getHeaders().keySet())
			conn.setRequestProperty(header, request.getHeaders().get(header));

		boolean postSomethingToServer = doesPost(request);
		conn.setDoOutput(postSomethingToServer);
		conn.setDoInput(true);
		conn.setConnectTimeout(connectionTimeoutMs);
		conn.connect();
		OutputStream out = null;
		if (postSomethingToServer) {
			out = conn.getOutputStream();
			out.write(request.getContent());
			out.flush();
		}
		return conn;
	}

	protected Map<String, String> getResponseHeaders(HttpURLConnection conn) {
		Map<String, String> headers = new HashMap<>();
		for (int n = 0; true; n++) {
			String headerName = conn.getHeaderFieldKey(n);
			String headerValue = conn.getHeaderField(n);
			// javadocs on getHeaderField says that field 0 may have a
			// special meaning; if the key is null but the value isn't then
			// we need to skip it.
			if (headerName == null && headerValue != null)
				continue;
			if (headerName == null)
				break;
			headers.put(headerName, headerValue);
		}
		return headers;
	}

	protected CompletableFuture<ResponseTask> readResponse(HttpURLConnection conn) throws Exception {
		CompletableFuture<ResponseTask> f = CompletableFuture.supplyAsync(() -> {
			try {
				ResponseTask r = new ResponseTask();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				r.setStatus(conn.getResponseCode());
				InputStream in = null;
				InputStream err = conn.getErrorStream();
				if (err != null)
					in = err;
				else
					in = conn.getInputStream();
				r.setStatusMessage(conn.getResponseMessage());
				r.getHeaders().putAll(getResponseHeaders(conn));
				IOUtils.copy(in, baos);
				r.setContent(baos.toByteArray());

				in.close();
				conn.disconnect();
				return r;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		return f;
	}

	public CompletableFuture<ResponseTask> execute(RequestTask request) throws IOException {
		try {
			HttpURLConnection conn = openConnection(request);
			return readResponse(conn);
		} catch (ConnectException ece) {
			throw new IOException("Couldn't connect to " + request.getUrl(), ece);
		} catch (RuntimeException er) {
			throw er;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
