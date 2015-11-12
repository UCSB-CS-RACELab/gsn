/**
 * Global Sensor Networks (GSN) Source Code
 * Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
 *
 * This file is part of GSN.
 *
 * GSN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GSN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GSN.  If not, see <http://www.gnu.org/licenses/>.
 *
 * File: src/gsn/wrappers/general/FileSystemMonitor.java
 */

package gsn.wrappers.general;

import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.StreamElement;
import gsn.wrappers.AbstractWrapper;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;
import org.apache.commons.vfs2.provider.local.LocalFile;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.*;
import java.util.regex.Pattern;

/**
 * Wrapper that monitors for any changed files in the directory specified in
 * the VSD XML file under the "folder" attribute. Changes are recorded in the
 * database as "created", "changed", "deleted", together with the file path.
 */
public class FileSystemMonitorWrapper extends AbstractWrapper {
    private static final int rate = 1000;
    private static DataField[] dataField = new DataField[]{
            new DataField("filePath", "varchar(1000)", "file path"),
            new DataField("timestamp", "bigint", "when the folder was updated"),
            new DataField("operationType", "varchar(10)", "update operation type")
    };
    private final transient Logger logger = Logger.getLogger(FileSystemMonitorWrapper.class);
    private int threadCounter = 0;
    private AddressBean addressBean;
    private Pattern pattern = null;

    public boolean initialize() {
        this.addressBean = getActiveAddressBean();
        try {
            FileSystemManager manager = VFS.getManager();
            DefaultFileMonitor fm = new DefaultFileMonitor(new FyleSystemListener());
            String folder = this.addressBean.getPredicateValue("folder");
            pattern = Pattern.compile(this.addressBean.getPredicateValue("pattern"));
            FileObject file = manager.resolveFile(folder);
            fm.setDelay(rate);
            fm.addFile(file);
            fm.start();
        } catch (FileSystemException e) {
            logger.info("FileSystemException.", e);
        }
        return true;
    }

    public void run() {
    }

    class FyleSystemListener implements FileListener {
        public void fileChanged(FileChangeEvent event) {
            recordEvent(event, "changed");
        }

        public void fileCreated(FileChangeEvent event) {
            recordEvent(event, "created");
        }

        public void fileDeleted(FileChangeEvent event) {
            recordEvent(event, "deleted");
        }

        private void recordEvent(FileChangeEvent event, String opertionType) {
            FileObject changedFile = event.getFile();
            String fileName = changedFile.getName().toString();
            logger.debug("filename: " + fileName);
            if (pattern.matcher(fileName).matches()) {
                logger.debug(opertionType + " " + fileName);
                StreamElement se = new StreamElement(dataField, new Serializable[]{
                        fileName, System.currentTimeMillis(), opertionType});
                postStreamElement(se);
            }
        }
    }

    public DataField[] getOutputFormat() {
        return dataField;
    }

    public String getWrapperName() {
        return "FileSystemMonitorWrapper";
    }

    public void dispose() {
        threadCounter--;
    }
}
