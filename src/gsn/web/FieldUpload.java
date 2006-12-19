package gsn.web;

import gsn.beans.StreamElement;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;

/**
 * @author Cl�ment Beffa (clement.beffa@epfl.ch)<br>
 * @web.servlet name="FieldUpload" load-on-startup="true"
 * @web.servlet-mapping url-pattern="/upload"
 */

public class FieldUpload extends HttpServlet {
	static final long serialVersionUID = 13;
	private static final transient Logger logger = Logger.getLogger( StreamElement.class );
	   
	public void  doGet ( HttpServletRequest req , HttpServletResponse res ) throws ServletException , IOException {
		doPost(req, res);
	}
	
	public void doPost ( HttpServletRequest req , HttpServletResponse res ) throws ServletException , IOException {
		PrintWriter out = res.getWriter();
		
		//Check that we have a file upload request
		boolean isMultipart = ServletFileUpload.isMultipartContent(req);
		if (!isMultipart) {
			out.write("not multipart!");
			logger.error("not multipart");
			return;
		}
		
		//Create a factory for disk-based file items
		FileItemFactory factory = new DiskFileItemFactory();

		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(factory);

		//Set overall request size constraint
		//upload.setSizeMax(1000);
		
		// Parse the request
		List /* FileItem */ items;
		try {
			items = upload.parseRequest(req);
		} catch(Exception e){
			logger.error("exception:",e);
			return;
		}
		
		// Process the uploaded items
		Iterator iter = items.iterator();
		String cmd = "";
		while (iter.hasNext()) {
		    FileItem item = (FileItem) iter.next();
		    
		    if (item.getFieldName().equals("cmd")){
		    	//define which cmd block is sent
		    	cmd = item.getString();
		    } else if (item.getFieldName().split(";")[0].equals(cmd)) {
		    	//only for the defined cmd
		    	if (item.isFormField()) {
		    		//normal field
		    		String name = item.getFieldName();
		    	    String value = item.getString();
		    	    
		    	    //do whatever with it
		    	    out.write("field:"+name+":"+value+"\n");
		    	} else {
		    		//file field
		    		String name = item.getFieldName();
		    	    String fileName = item.getName();
		    	    String contentType = item.getContentType();
		    	    boolean isInMemory = item.isInMemory();
		    	    long sizeInBytes = item.getSize();
		    	    
		    	    //do whatever with it
		    	    out.write("file:"+name+":"+fileName+":"+contentType+":"+isInMemory+":"+sizeInBytes+"\n");
		    	}
		    }
		}
		
		//if no error, send a successful redirect
	    //res.sendRedirect("/#home,msg=upsucc");
	}
}