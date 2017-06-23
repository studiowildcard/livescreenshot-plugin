/**
 * Copyright 2013 Dr. Stefan Schimanski <sts@1stein.org>
 * Copyright 2017 Harald Sitter <sitter@kde.org>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jenkinsci.plugins.livescreenshot;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Run;
import jenkins.util.VirtualFile;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author sts
 */
public class LiveScreenshotAction implements Action {
	private Run<?,?> build;
	private final String fullscreenFilename;
	private final String thumbnailFilename;
	private FilePath workspace;
	
	public String getFullscreenFilename() {
		return fullscreenFilename;
	}

	public String getThumbnailFilename() {
		return thumbnailFilename;
	}
	
	public LiveScreenshotAction(Run<?,?> build, FilePath workspace, String fullscreenFilename, String thumbnailFilename) {
		this.build = build;
		this.workspace = workspace;
		this.fullscreenFilename = fullscreenFilename;
		this.thumbnailFilename = thumbnailFilename;
	}

	public Run<?, ?> getBuild() {
		return this.build;
	}
	
	public String getDisplayName() {
		return "Screenshot";
	}

	public String getIconFileName() {
		return "monitor.gif";
	}

	public String getUrlName() {
		return "screenshot";
	}

	public FilePath getWorkspace() {
		return this.workspace;
	}

	public void doDynamic(StaplerRequest request, StaplerResponse rsp)
			throws Exception {
		// which file to load?
		String path = request.getRestOfPath();
		String filename = null;
		if (path.equals("/thumb")) {
			filename = this.thumbnailFilename;
		} else if (path.equals("/full")) {
			filename = this.fullscreenFilename;
		} else {
			return;
		}
			
		// load image
		byte[] bytes;
		try {
			bytes = screenshot(filename);
		}
		catch (IOException e) {
			return;
		}
			
		// output image
		if (filename.endsWith(".PNG") || filename.endsWith(".png"))
			rsp.setContentType("image/png");
		else if (filename.endsWith(".JPG") || filename.endsWith(".jpg"))
			rsp.setContentType("image/jpeg");
		else
			return;
		rsp.setContentLength(bytes.length);
		ServletOutputStream sos = rsp.getOutputStream();
		sos.write(bytes);
		sos.flush();
		sos.close();
	}

	public byte[] readContent(InputStream is, long length) throws IOException {
		byte[] bytes = new byte[(int)length];
		
		// Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }
		
		return bytes;
	}
	
	public byte[] noScreenshotFile() throws IOException {
		InputStream is = this.getClass().getResourceAsStream("noscreenshot.png");
		try {
			return this.readContent(is, is.available());
		} finally {
			is.close();
		}
	}
	
	public byte[] screenshotArtifact(String filename) throws IOException {
		VirtualFile file = build.getArtifactManager().root().child("screenshots/"+filename);
		if (file.isFile()) {
			InputStream fis = file.open();
			byte[] bytes = readContent(fis, file.length());
			fis.close();
			return bytes;
		}

		return null;
	}
	
	public byte[] liveScreenshot(String filename) throws IOException {
		try {
			// return workspace file
			FilePath fp = workspace.child(filename);
			if (!fp.exists()) {
				return this.noScreenshotFile();
			}
			InputStream is = fp.read();
			byte[] bytes = readContent(is, fp.length());
			is.read(bytes);
			return bytes;
		}
		catch (InterruptedException ex) {
			return this.noScreenshotFile();
		}
	}
	
	public byte[] screenshot(String filename) throws IOException {
		// try to find artifact
		byte[] bytes = screenshotArtifact(filename);
		if (bytes != null)
			return bytes;

		return liveScreenshot(filename);
	}
}
