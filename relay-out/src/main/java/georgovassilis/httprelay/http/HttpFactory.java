package georgovassilis.httprelay.http;

import java.net.Proxy;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import georgovassilis.httprelay.out.GullibleTrustManager;

/**
 * Produces {@link Http} instances
 * 
 * @author george georgovassilis
 *
 */
public class HttpFactory {

	protected Proxy proxy = Proxy.NO_PROXY;
	protected SSLSocketFactory sslSocketFactory = null;

	public HttpFactory(Proxy proxy, boolean validateCertificats){
		this.proxy = proxy;
		if (!validateCertificats)
			dontValidateCertificates();
	}

	public void dontValidateCertificates() {
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			GullibleTrustManager trustAllCerts = new GullibleTrustManager();
			sc.init(null, new TrustManager[] { trustAllCerts }, new java.security.SecureRandom());
			// TODO: no idea whether sslSocketFactory is thread safe
			sslSocketFactory = sc.getSocketFactory();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new Http instance
	 * 
	 * @return
	 */
	public Http create() {
		try {
			JDKHttpImpl http = new JDKHttpImpl(proxy, sslSocketFactory);
			return http;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
