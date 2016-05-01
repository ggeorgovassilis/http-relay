package georgovassilis.httprelay.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
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

	protected Future<ResponseTask> readResponse(HttpURLConnection conn) throws Exception {
		ResponseTask r = new ResponseTask();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		InputStream in = conn.getInputStream();
		r.setStatus(conn.getResponseCode());
		r.setStatusMessage(conn.getResponseMessage());
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
			r.getHeaders().put(headerName, headerValue);
		}

		IOUtils.copy(in, baos);
		r.setContent(baos.toByteArray());

		in.close();
		conn.disconnect();

		CompletableFuture<ResponseTask> f = new CompletableFuture<ResponseTask>();
		f.complete(r);
		return f;
	}

	public Future<ResponseTask> execute(RequestTask request) throws IOException {
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
