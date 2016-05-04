package georgovassilis.httprelay.out;

import java.net.Proxy;
import java.util.concurrent.Future;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;
import georgovassilis.httprelay.http.Http;
import georgovassilis.httprelay.http.JDKHttpImpl;

/**
 * Private relay which polls a public relay for {@link RequestTask}s, sends them as HTTP requests to a backend web server, then
 * packages the response as a {@link ResponseTask} and sends it back to the public relay.
 * @author george georgovassilis
 *
 */
public class PrivateRelayProxy implements Runnable {

	protected Logger log = LogManager.getLogger(getClass().getName());
	protected long pauseOnErrorMs = 10000;
	protected String getNextTaskFromUrl;
	protected String backendUrl;
	protected ObjectMapper mapper = new ObjectMapper();
	protected Proxy backendProxy;
	protected Proxy relayProxy;
	
	
	public PrivateRelayProxy(Proxy backendProxy, Proxy relayProxy, long pauseOnErrorMs, String getNextTaskFromUrl, String backendUrl){
		this.backendProxy = backendProxy;
		this.relayProxy = relayProxy;
		this.pauseOnErrorMs = pauseOnErrorMs;
		this.getNextTaskFromUrl = getNextTaskFromUrl;
		this.backendUrl = backendUrl;
	}

	protected RequestTask getNextRequest() throws Exception {
		log.info("Asking public relay for next task");
		Http http = new JDKHttpImpl(relayProxy);
		RequestTask request = new RequestTask("GET", getNextTaskFromUrl);
		Future<ResponseTask> future = http.execute(request);
		ResponseTask response = future.get();
		if (response.getStatus() == HttpServletResponse.SC_NO_CONTENT) {
			log.info("No new tasks yet, trying again later");
			return null;
		}
		RequestTask requestTask = mapper.readValue(response.getContent(), RequestTask.class);
		log.info("Public relay gave task " + requestTask.getId());
		return requestTask;
	}

	protected void submitResponse(ResponseTask responseTask) throws Exception {
		log.info("Submitting response to public relay for task " + responseTask.getId());

		Http http = new JDKHttpImpl(relayProxy);
		RequestTask responseToRelay = new RequestTask("POST", getNextTaskFromUrl);
		responseToRelay.getHeaders().put("content-type", "application/json; charset=utf-8");
		byte[] content = mapper.writeValueAsBytes(responseTask);
		responseToRelay.getHeaders().put("content-length", "" + content.length);
		responseToRelay.setContent(content);
		Future<ResponseTask> f = http.execute(responseToRelay);
		ResponseTask r = f.get();
		log.info("Finished submitting response to public relay for task " + responseTask.getId() + ", relay responded with "
				+ r.getStatus());
	}

	protected ResponseTask forwardRequestToWebServer(RequestTask requestTask) throws Exception {
		log.info("Sending request "+requestTask.getId()+" to "+requestTask.getUrl()+" to webserver");
		Http http = new JDKHttpImpl(backendProxy);
		requestTask.setUrl(backendUrl + requestTask.getUrl());
		Future<ResponseTask> f = http.execute(requestTask);

		ResponseTask responseTask = f.get();
		responseTask.setId(requestTask.getId());
		log.info("Webserver returned with task "+requestTask.getId()+" and status code "+responseTask.getStatus());
		return responseTask;
	}

	protected void processNextRequest() throws Exception {
		RequestTask requestTask = getNextRequest();
		if (requestTask != null) {
			ResponseTask responseTask = forwardRequestToWebServer(requestTask);
			submitResponse(responseTask);
		}
	}

	public void run() {
		while (true) {
			try {
				processNextRequest();
			} catch (Exception e) {
				log.warn(e, e);
				try {
					log.warn("Backing off for " + pauseOnErrorMs + " ms before trying to contact task hub again");
					Thread.sleep(pauseOnErrorMs);
				} catch (InterruptedException e1) {
				}
			}
		}
	}

	static void ignoreCertificateValidation() throws Exception {
		SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(null, new TrustManager[] { new GullibleTrustManager() }, new java.security.SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String string, SSLSession ssls) {
				return true;
			}
		});
	}


	public static void main(String[] args) throws Exception {

		ignoreCertificateValidation();
		PrivateRelayProxyFactory proxyFactory = new PrivateRelayProxyFactory();
		PrivateRelayProxy server = proxyFactory.createServerProxy(args);
		server.run();
	}
}
