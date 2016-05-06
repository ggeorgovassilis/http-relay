package georgovassilis.httprelay.in;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

/**
 * Abusing this servlet to set up any shared objects between other servlets. I 
 * should read up on the servlet container specification for a better way...
 * @author george georgovassilis
 *
 */

public class ApplicationConfiguration extends HttpServlet {

	/**
	 * Shared reference to the {@link TaskHub}
	 */
	protected TaskHub taskHub;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		taskHub = new TaskHub();
		taskHub.start();
		config.getServletContext().setAttribute(TaskHub.class.getName(), taskHub);
	}

	@Override
	public void destroy() {
		taskHub.close();
		super.destroy();
		
	}
}
