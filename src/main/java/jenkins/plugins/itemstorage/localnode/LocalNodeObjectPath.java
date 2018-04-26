/*
 * The MIT License
 *
 * Copyright 2016 Peter Hayes.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.itemstorage.localnode;

import hudson.FilePath;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.ObjectPath;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * This implements the on local node storage for object paths
 *
 * @author Daniel Beland
 */
public class LocalNodeObjectPath extends ObjectPath {
    private static final Logger LOGGER = Logger.getLogger(LocalNodeObjectPath.class.getName());

    private Item item;
    private FilePath workspace;
    private String path;

    private VirtualChannel channel;

    public LocalNodeObjectPath(Item item, FilePath workspace, String path) {
        this.item = item;
        this.workspace = workspace;
        this.path = path;

        // Workspace could be null when this is used outside of a build (browsing the cache content)
        if (workspace != null) {
        	channel = workspace.getChannel();
        }
    }

    @Override
    public ObjectPath child(String path) throws IOException, InterruptedException {
        return new LocalNodeObjectPath(item, workspace, this.path + "/" + path);
    }

    @Override
    public int copyRecursiveTo(String fileMask, String excludes, FilePath target) throws IOException, InterruptedException {
        LOGGER.info("Copying from " + path + " to " + target + " locally");

        FilePath localCache = getLocalFilePath(target.getChannel());

        return localCache.copyRecursiveTo(fileMask, excludes, target);

    }

    @Override
    public int copyRecursiveFrom(String fileMask, String excludes, FilePath source) throws IOException, InterruptedException {
        LOGGER.info("Copying from " + source + " to " + path + " locally");

        FilePath localCache = getLocalFilePath(source.getChannel());

        return source.copyRecursiveTo(fileMask, excludes, localCache);
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
    	if (channel == null) {
    		// Hack, try to find a node where the path exists
    		List<Node> nodes = new ArrayList<>(Jenkins.getInstance().getNodes());
    		nodes.add(Jenkins.getInstance());
    		for (Node node : nodes) {
    			if (getLocalFilePathOnNode(node).exists()) {
    				channel = node.getChannel();
    				break;
    			}
    		}
    	}

    	return getLocalFilePath().exists();
    }

    @Override
    public void deleteRecursive() throws IOException, InterruptedException {
    	getLocalFilePath().deleteRecursive();
    }

    @Override
    public HttpResponse browse(StaplerRequest request, StaplerResponse response, Job job, String name) {
		try {
	        return new DirectoryBrowserSupport(job, getLocalFilePath(), "Cache of " + name, "folder.png", true);
		} catch (IOException | InterruptedException e) {
		}

		return null;
    }

    private String getRelativePath() throws IOException, InterruptedException {
		File jobsFolder = new File(item.getRootDir().getParentFile().getName());
		File jobFolder = new File(jobsFolder, item.getRootDir().getName());
		return new File(jobFolder, path).getPath();
    }

    private FilePath getLocalFilePath() throws IOException, InterruptedException {
    	// Fall back on the master node
    	VirtualChannel virtualChannel = channel != null ? channel : Jenkins.getInstance().getChannel();

    	return FilePath.getHomeDirectory(virtualChannel).child(getRelativePath());
    }

    private FilePath getLocalFilePath(VirtualChannel virtualChannel) throws IOException, InterruptedException {
    	return FilePath.getHomeDirectory(virtualChannel).child(getRelativePath());
    }

    private FilePath getLocalFilePathOnNode(Node node) throws IOException, InterruptedException {
    	return FilePath.getHomeDirectory(node.getChannel()).child(getRelativePath());
    }
}
