/*
 * This file is part of the Alitheia system, developed by the SQO-OSS
 * consortium as part of the IST FP6 SQO-OSS project, number 033331.
 *
 * Copyright 2008 Athens University of Economics and Business
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package eu.sqooss.impl.service.updater;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.sqooss.core.AlitheiaCore;
import eu.sqooss.service.db.Bug;
import eu.sqooss.service.db.BugResolution;
import eu.sqooss.service.db.BugResolution.Resolution;
import eu.sqooss.service.db.BugStatus;
import eu.sqooss.service.db.BugStatus.Status;
import eu.sqooss.service.db.BugSeverity;
import eu.sqooss.service.db.BugSeverity.Severity;
import eu.sqooss.service.db.BugPriority;
import eu.sqooss.service.db.BugPriority.Priority;
import eu.sqooss.service.db.DBService;
import eu.sqooss.service.db.Developer;
import eu.sqooss.service.db.StoredProject;
import eu.sqooss.service.logging.Logger;
import eu.sqooss.service.scheduler.Job;
import eu.sqooss.service.tds.BTSAccessor;
import eu.sqooss.service.tds.BTSEntry;
import eu.sqooss.service.updater.UpdaterException;

/**
 * Bug updater. Reads data from the TDS and updates 
 */
public class BugUpdater extends Job {

    private DBService db;
    private Logger log;
    private BTSAccessor bts;
    private StoredProject sp;
    
    public BugUpdater(StoredProject project, UpdaterServiceImpl updater,
            AlitheiaCore core, Logger logger) throws UpdaterException {
        
        this.db = core.getDBService();
        this.log = logger;
        this.bts = core.getTDSService().getAccessor(project.getId()).getBTSAccessor();
        this.sp = project;
    }

    public int priority() {
        return 0x1;
    }

    protected void run() throws Exception {
        db.startDBSession();
        //Get latest updated date
        List<String> bugIds = null;
        
        if (Bug.getLastUpdate(sp) != null) {
            bugIds = bts.getBugsNewerThan(Bug.getLastUpdate(sp).getUpdateRun());
        } else {
            bugIds = bts.getAllBugs();
        }
        log.info("Got " + bugIds.size() + " new bugs");

        //Update
        for (String bugID : bugIds) {
            if (bugExists(sp, bugID)) {
                log.debug("Updating existing bug " + bugID);
            }
            db.addRecord(BTSEntryToBug(bts.getBug(bugID)));
            log.debug("Added bug " + bugID);
        }
        db.commitDBSession();
    }
    
    /**
     * Convert a BTS entry to a Bug DAO
     */
    private Bug BTSEntryToBug (BTSEntry b) {
        if (b == null)
            return null;
        
        Bug bug = new Bug();
        bug.setBugID(b.bugID);
        bug.setCreationTS(b.creationTimestamp);
        bug.setDeltaTS(b.latestUpdateTimestamp);
        
        if (b.priority != null) {
            bug.setPriority(BugPriority.getBugPriority(Priority.fromString(b.priority.toString())));
        } else {
            bug.setPriority(BugPriority.getBugPriority(Priority.UNKNOWN));
        }   
        bug.setProject(sp);
        
        if (b.resolution != null) {
            bug.setResolution(BugResolution.getBugResolution(Resolution.fromString(b.resolution.toString())));
        } else {
            bug.setResolution(BugResolution.getBugResolution(Resolution.UNKNOWN));
        }
        
        if (b.severity != null) {
            bug.setSeverity(BugSeverity.getBugseverity(Severity.fromString(b.severity.toString())));
        } else {
            bug.setSeverity(BugSeverity.getBugseverity(Severity.UNKNOWN));
        }
        
        if (b.state != null) {
            bug.setStatus(BugStatus.getBugStatus(Status.fromString(b.state.toString())));
        } else {
            bug.setStatus(BugStatus.getBugStatus(Status.UNKNOWN));
        }
        
        bug.setShortDesc(b.shortDescr);
        bug.setUpdateRun(new Date(System.currentTimeMillis()));

        Developer d = null;
        if (b.assignee.matches("@")) {
            d = Developer.getDeveloperByEmail(b.assignee, sp);
        } else {
            d = Developer.getDeveloperByUsername(b.assignee, sp);
        }
        
        bug.setReporter(d);
        
        return bug;
    }
    
    /**
     * Check if there is an entry in the database with this bug id
     */
    private boolean bugExists(StoredProject sp, String bugId) {
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("project", sp);
        params.put("bugID", bugId);
        
        List<Bug> buglist = db.findObjectsByProperties(Bug.class, params);
        
        if (buglist.isEmpty())
            return false;
        return true;
    }
    
   
}
