package georgovassilis.httprelay.in;

import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.util.ReflectionUtils;

import com.github.ggeorgovassilis.mockitools.BaseAnswer;

import georgovassilis.httprelay.common.RequestTask;
import georgovassilis.httprelay.common.ResponseTask;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit test for {@link PublicRelayServlet}
 * @author george georgovassilis
 *
 */
public class PublicRelayServletTest {

	PublicRelayServlet publicRelay;
	MockServletConfig servletConfig;
	MockServletContext servletContext;
	TaskHub taskHub;
	
	@Before
	public void setup() throws Exception{
		taskHub = mock(TaskHub.class);
		servletContext = new MockServletContext();
		servletConfig = new MockServletConfig(servletContext);
		servletContext.setAttribute(TaskHub.class.getName(), taskHub);
		
		publicRelay = new PublicRelayServlet();
		publicRelay.init(servletConfig);
	}
	
	@After
	public void teardown(){
		publicRelay.destroy();
	}
	
	@Test
	public void test_submit_request() throws Exception{
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAsyncSupported(true);
		request.setMethod("GET");
		request.setRequestURI("http://proxied-website.test/");
		
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockAsyncContext asyncContext = new MockAsyncContext(request, response);
		request.setAsyncContext(asyncContext);
		
		final ResponseTask expectedResponse = new ResponseTask();
		expectedResponse.setStatus(200);
		expectedResponse.setContent("test response".getBytes());
		expectedResponse.getHeaders().put("X-Response-Header", "header value");
		
		
		when(taskHub.submitRequestTask(any(RequestTask.class), any(RequestCallback.class))).then(new BaseAnswer<Void>() {
			Void execute(RequestTask requestTask, RequestCallback callback){
				expectedResponse.setId(requestTask.getId());
				callback.onResponseReceived(expectedResponse);
				return null;
			}
		});
		
		publicRelay.service(request, response);
		assertEquals(200, response.getStatus());
		assertEquals("header value",response.getHeader("X-Response-Header"));
		assertEquals("test response", response.getContentAsString());
	}
}
