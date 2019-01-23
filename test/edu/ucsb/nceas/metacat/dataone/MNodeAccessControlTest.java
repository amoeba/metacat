/**
 *  '$RCSfile$'
 *  Copyright: 2010 Regents of the University of California and the
 *              National Center for Ecological Analysis and Synthesis
 *  Purpose: To test the Access Controls in metacat by JUnit
 *
 *   '$Author$'
 *     '$Date$'
 * '$Revision$'
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.ucsb.nceas.metacat.dataone;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.dataone.client.D1Node;
import org.dataone.client.NodeLocator;
import org.dataone.client.exception.ClientSideException;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.configuration.Settings;
import org.dataone.service.exceptions.InvalidRequest;
import org.dataone.service.exceptions.InvalidToken;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.SynchronizationFailed;
import org.dataone.service.types.v1.AccessPolicy;
import org.dataone.service.types.v1.AccessRule;
import org.dataone.service.types.v1.Checksum;
import org.dataone.service.types.v1.DescribeResponse;
import org.dataone.service.types.v1.Event;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v1.NodeReference;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v1.ObjectList;
import org.dataone.service.types.v1.Permission;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;
import org.dataone.service.types.v1.util.AuthUtils;
import org.dataone.service.types.v2.Log;
import org.dataone.service.types.v2.Node;
import org.dataone.service.types.v2.OptionList;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.util.Constants;

import edu.ucsb.nceas.utilities.IOUtil;
import junit.framework.Test;
import junit.framework.TestSuite;

public class MNodeAccessControlTest extends D1NodeServiceTest {
   
    public static final String TEXT = "data";
    public static final String ALGORITHM = "MD5";
    public static final String KNBAMDINMEMBERSUBJECT = "http://orcid.org/0000-0003-2192-431X";
    public static final String PISCOMANAGERMEMBERSUBJECT = "CN=Michael Frenock A5618,O=Google,C=US,DC=cilogon,DC=org";
    private static final Session nullSession = null;
    private static final Session publicSession = getPublicUser();
    private static Session KNBadmin = null;
    private static Session PISCOManager = null;
    
    /**
     * Constructor
     * @param name
     */
    public MNodeAccessControlTest(String name) {
        super(name);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new MNodeAccessControlTest("initialize"));
        suite.addTest(new MNodeAccessControlTest("testMethodsWithoutSession"));
        suite.addTest(new MNodeAccessControlTest("testMethodsWithSession"));
        return suite;
    }
    
    /**
     * Establish a testing framework by initializing appropriate objects
     */
    public void setUp() throws Exception {
        //Use the default CN
        D1Client.setNodeLocator(null);
    }
    
    /**
     * Run an initial test that always passes to check that the test harness is
     * working.
     */
    public void initialize() {
        printTestHeader("initialize");
        try {
            /*Session session =getCNSession();
            System.out.println("==================the cn session is "+session.getSubject().getValue());
            Session userSession = getOneKnbDataAdminsMemberSession();
            Set<Subject> subjects = AuthUtils.authorizedClientSubjects(userSession);
            for (Subject subject: subjects) {
                System.out.println("the knb data admin user has this subject "+subject.getValue());
            }
             userSession = getOnePISCODataManagersMemberSession();
             subjects = AuthUtils.authorizedClientSubjects(userSession);
            for (Subject subject: subjects) {
                System.out.println("the pisco data manager user has this subject "+subject.getValue());
            }*/
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertTrue(1 == 1);
    }
    
    /**
     * Test those methods which don't need sessions.
     * @throws Exception
     */
    public void testMethodsWithoutSession() throws Exception {
        testGetCapacity();
        testListObjects();
        testListViews();
    }
    
    /**
     * Test those methods which need sessions.
     * @throws Exception
     */
    public void testMethodsWithSession() throws Exception {
        KNBadmin = getOneKnbDataAdminsMemberSession();
        PISCOManager = getOnePISCODataManagersMemberSession();
        //rights holder is a user.
        Subject rightsHolder = getAnotherSession().getSubject();
        testMethodsWithGivenHightsHolder(rightsHolder);
    }
    
    /**
     * Real methods to test the access control - 
     * @param rightsHolder
     * @throws Exception
     */
    private void testMethodsWithGivenHightsHolder(Subject rightsHolder) throws Exception {
        Session rightsHolderSession = new Session();
        rightsHolderSession.setSubject(rightsHolder);
        Session submitter = getTestSession();
       
        //1. Test generating identifiers (it only checks if session is null)
        String scheme = "unknow";
        String fragment = "test-access"+System.currentTimeMillis();
        testGenerateIdentifier(nullSession, scheme, fragment, false);
        Identifier id1 = testGenerateIdentifier(publicSession, scheme, fragment, true);
        
        //2 Test the create method (it only checks if session is null)
        InputStream object = new ByteArrayInputStream(TEXT.getBytes("UTF-8"));
        SystemMetadata sysmeta = createSystemMetadata(id1, submitter.getSubject(), object);
        sysmeta.setRightsHolder(rightsHolder);
        sysmeta.setAccessPolicy(new AccessPolicy());//no access policy
        testCreate(nullSession, id1, sysmeta, object, false);
        testCreate(submitter, id1, sysmeta, object, true);
       
        //3 The object id1 doesn't have any access policy, it can be read by rights holder, cn and mn.
        testGetAPI(getCNSession(), id1, sysmeta.getChecksum(), true);//cn can read it
        testGetAPI(getMNSession(), id1, sysmeta.getChecksum(), true);//mn can read it
        testGetAPI(rightsHolderSession, id1, sysmeta.getChecksum(), true);//rightsholder can read it
        testGetAPI(submitter, id1,sysmeta.getChecksum(),false); //submitter can't read it
        testGetAPI(publicSession, id1,sysmeta.getChecksum(),false); //public can't read it
        testGetAPI(KNBadmin, id1,sysmeta.getChecksum(),false); //knb can't read it
        testGetAPI(PISCOManager, id1,sysmeta.getChecksum(),false); //pisco can't read it
        testGetAPI(nullSession, id1,sysmeta.getChecksum(),false); //nullSession can't read it
        testIsAuthorized(getCNSession(), id1, Permission.CHANGE_PERMISSION, true);//cn can read it
        testIsAuthorized(getMNSession(), id1, Permission.CHANGE_PERMISSION, true);//mn can read it
        testIsAuthorized(rightsHolderSession, id1, Permission.CHANGE_PERMISSION, true);//rightsholder can read it
        testIsAuthorized(submitter, id1,Permission.READ,false); //submitter can't read it
        testIsAuthorized(publicSession, id1, Permission.READ,false); //public can't read it
        testIsAuthorized(KNBadmin, id1,Permission.READ,false); //knb can't read it
        testIsAuthorized(PISCOManager, id1,Permission.READ,false); //pisco can't read it
        testIsAuthorized(nullSession, id1,Permission.READ,false); //nullSession can't read it
       
        //4 Test update the system metadata with new access rule (knb group can read it)
        AccessPolicy policy = new AccessPolicy();
        AccessRule rule = new AccessRule();
        rule.addPermission(Permission.READ);
        rule.addSubject(getKnbDataAdminsGroupSubject());
        policy.addAllow(rule);
        sysmeta.setAccessPolicy(policy);
        testUpdateSystemmetadata(nullSession, id1, sysmeta, false);
        testUpdateSystemmetadata(publicSession, id1, sysmeta, false);
        testUpdateSystemmetadata(submitter, id1, sysmeta, false);
        testUpdateSystemmetadata(PISCOManager, id1, sysmeta, false);
        testUpdateSystemmetadata(KNBadmin, id1, sysmeta, false);
        testUpdateSystemmetadata(rightsHolderSession, id1, sysmeta, true);
        testUpdateSystemmetadata(getCNSession(), id1, sysmeta, true);
        testUpdateSystemmetadata(getMNSession(), id1, sysmeta, true);
        //read it with the access rule - knb group add read it
        testGetAPI(getCNSession(), id1, sysmeta.getChecksum(), true);//cn can read it
        testGetAPI(getMNSession(), id1, sysmeta.getChecksum(), true);//mn can read it
        testGetAPI(rightsHolderSession, id1, sysmeta.getChecksum(), true);//rightsholder can read it
        testGetAPI(submitter, id1,sysmeta.getChecksum(),false); //submitter can't read it
        testGetAPI(publicSession, id1,sysmeta.getChecksum(),false); //public can't read it
        testGetAPI(KNBadmin, id1,sysmeta.getChecksum(),true); //knb can read it
        testGetAPI(PISCOManager, id1,sysmeta.getChecksum(),false); //pisco can't read it
        testGetAPI(nullSession, id1,sysmeta.getChecksum(),false); //nullSession can't read it
        testIsAuthorized(submitter, id1,Permission.READ,false); 
        testIsAuthorized(publicSession, id1, Permission.READ,false); 
        testIsAuthorized(KNBadmin, id1,Permission.READ,true); 
        testIsAuthorized(PISCOManager, id1,Permission.READ,false); 
        testIsAuthorized(nullSession, id1,Permission.READ,false); 
        
        //5.Test get api when knb group has the write permission and submitter has read permission
        //set up
        policy = new AccessPolicy();
        rule = new AccessRule();
        rule.addPermission(Permission.WRITE);
        rule.addSubject(getKnbDataAdminsGroupSubject());
        policy.addAllow(rule);
        AccessRule rule2= new AccessRule();
        rule2.addPermission(Permission.READ);
        rule2.addSubject(submitter.getSubject());
        policy.addAllow(rule2);
        sysmeta.setAccessPolicy(policy);
        testUpdateSystemmetadata(rightsHolderSession, id1, sysmeta, true);
        //read
        testGetAPI(getCNSession(), id1, sysmeta.getChecksum(), true);//cn can read it
        testGetAPI(getMNSession(), id1, sysmeta.getChecksum(), true);//mn can read it
        testGetAPI(rightsHolderSession, id1, sysmeta.getChecksum(), true);//rightsholder can read it
        testGetAPI(submitter, id1,sysmeta.getChecksum(),true); //submitter can read it
        testGetAPI(publicSession, id1,sysmeta.getChecksum(),false); //public can't read it
        testGetAPI(KNBadmin, id1,sysmeta.getChecksum(),true); //knb can read it
        testGetAPI(PISCOManager, id1,sysmeta.getChecksum(),false); //pisco can't read it
        testGetAPI(nullSession, id1,sysmeta.getChecksum(),false); //nullSession can't read it
        testIsAuthorized(submitter, id1,Permission.READ,true); 
        testIsAuthorized(publicSession, id1, Permission.READ,false); 
        testIsAuthorized(KNBadmin, id1,Permission.WRITE,true); 
        testIsAuthorized(PISCOManager, id1,Permission.READ,false); 
        testIsAuthorized(nullSession, id1,Permission.READ,false); 
        
        //6. Test get api when the public and submitter has the read permission and the knb-admin group has write permission
        //set up
        AccessRule rule3= new AccessRule();
        rule3.addPermission(Permission.READ);
        rule3.addSubject(publicSession.getSubject());
        policy.addAllow(rule3);
        sysmeta.setAccessPolicy(policy);
        testUpdateSystemmetadata(KNBadmin, id1, sysmeta, false);
        testUpdateSystemmetadata(rightsHolderSession, id1, sysmeta, true);
        //read
        testGetAPI(getCNSession(), id1, sysmeta.getChecksum(), true);//cn can read it
        testGetAPI(getMNSession(), id1, sysmeta.getChecksum(), true);//mn can read it
        testGetAPI(rightsHolderSession, id1, sysmeta.getChecksum(), true);//rightsholder can read it
        testGetAPI(submitter, id1,sysmeta.getChecksum(),true); //submitter can read it
        testGetAPI(publicSession, id1,sysmeta.getChecksum(),true); //public can read it
        testGetAPI(KNBadmin, id1,sysmeta.getChecksum(),true); //knb can read it
        testGetAPI(PISCOManager, id1,sysmeta.getChecksum(),true); //pisco can read it
        testGetAPI(nullSession, id1,sysmeta.getChecksum(),true); //nullSession can read it
        testIsAuthorized(submitter, id1,Permission.READ,true); 
        testIsAuthorized(publicSession, id1, Permission.READ,true); 
        testIsAuthorized(KNBadmin, id1,Permission.READ,true); 
        testIsAuthorized(PISCOManager, id1,Permission.READ,true); 
        testIsAuthorized(nullSession, id1,Permission.READ,true); 
        
        //7. Test the updateSystemMetadata (the public and submitter has the read permission and the knb-admin group has write permission)
        //add a new policy that pisco group and submitter has the change permission, and third user has the read permission
        AccessRule rule4= new AccessRule();
        rule4.addPermission(Permission.CHANGE_PERMISSION);
        rule4.addSubject(getPISCODataManagersGroupSubject());
        policy.addAllow(rule4);
        AccessRule rule5= new AccessRule();
        rule5.addPermission(Permission.CHANGE_PERMISSION);
        rule5.addSubject(submitter.getSubject());
        policy.addAllow(rule5);
        AccessRule rule6= new AccessRule();
        rule6.addPermission(Permission.READ);
        rule6.addSubject(getThirdUser().getSubject());
        policy.addAllow(rule6);
        sysmeta.setAccessPolicy(policy);
        testUpdateSystemmetadata(nullSession, id1, sysmeta, false);
        testUpdateSystemmetadata(publicSession, id1, sysmeta, false);
        testUpdateSystemmetadata(submitter, id1, sysmeta, false);
        testUpdateSystemmetadata(PISCOManager, id1, sysmeta, false);
        testUpdateSystemmetadata(KNBadmin, id1, sysmeta, false);
        testUpdateSystemmetadata(rightsHolderSession, id1, sysmeta, true);
        testUpdateSystemmetadata(getCNSession(), id1, sysmeta, true);
        testUpdateSystemmetadata(getMNSession(), id1, sysmeta, true);
        //now pisco member session and submitter can update systememetadata since they have change permssion
        testUpdateSystemmetadata(nullSession, id1, sysmeta, false);
        testUpdateSystemmetadata(publicSession, id1, sysmeta, false);
        testUpdateSystemmetadata(submitter, id1, sysmeta, true);
        testUpdateSystemmetadata(PISCOManager, id1, sysmeta, true);
        testUpdateSystemmetadata(KNBadmin, id1, sysmeta, false);
        testUpdateSystemmetadata(rightsHolderSession, id1, sysmeta, true);
        testUpdateSystemmetadata(getCNSession(), id1, sysmeta, true);
        testUpdateSystemmetadata(getMNSession(), id1, sysmeta, true);
        testIsAuthorized(submitter, id1,Permission.CHANGE_PERMISSION,true); 
        testIsAuthorized(getThirdUser(), id1,Permission.READ,true); 
        testIsAuthorized(publicSession, id1, Permission.READ,true); 
        testIsAuthorized(KNBadmin, id1,Permission.WRITE,true); 
        testIsAuthorized(PISCOManager, id1,Permission.CHANGE_PERMISSION,true); 
        testIsAuthorized(nullSession, id1,Permission.READ,true); 
        
        //8. Test update. Now the access policy for id1 is: the public and the third user has the read permission and the knb-admin group has write permission, and submitter and pisco group has the change permission.
        Identifier id2 = testGenerateIdentifier(submitter, scheme, "test-access"+System.currentTimeMillis(), true);
        sysmeta.setIdentifier(id2);
        testUpdate(nullSession, id1, sysmeta, id2, false);
        testUpdate(publicSession, id1, sysmeta, id2, false);
        testUpdate(getThirdUser(), id1, sysmeta, id2, false);
        testUpdate(KNBadmin, id1, sysmeta, id2, true);
        Thread.sleep(100);
        Identifier id3 = testGenerateIdentifier(submitter, scheme, "test-access"+System.currentTimeMillis(), true);
        sysmeta.setIdentifier(id3);
        sysmeta.setObsoletes(id2);
        testUpdate(PISCOManager, id2, sysmeta, id3, true);
        Thread.sleep(100);
        Identifier id4 = testGenerateIdentifier(submitter, scheme, "test-access"+System.currentTimeMillis(), true);
        sysmeta.setIdentifier(id4);
        sysmeta.setObsoletes(id3);
        testUpdate(rightsHolderSession, id3, sysmeta, id4, true);
        Thread.sleep(100);
        Identifier id5 = testGenerateIdentifier(submitter, scheme, "test-access"+System.currentTimeMillis(), true);
        sysmeta.setIdentifier(id5);
        sysmeta.setObsoletes(id4);
        testUpdate(getMNSession(), id4, sysmeta, id5, true);
        Thread.sleep(100);
        Identifier id6 = testGenerateIdentifier(submitter, scheme, "test-access"+System.currentTimeMillis(), true);
        sysmeta.setIdentifier(id6);
        sysmeta.setObsoletes(id5);
        testUpdate(getCNSession(), id5, sysmeta, id6, true);
        Thread.sleep(100);
        Identifier id7 = testGenerateIdentifier(submitter, scheme, "test-access"+System.currentTimeMillis(), true);
        sysmeta.setIdentifier(id7);
        sysmeta.setObsoletes(id6);
        testUpdate(submitter, id6, sysmeta, id7, true);
        
        System.out.println("The id is ============================"+id1.getValue());
        
        //testGetReplica(getCNSession(), id1, true);
        
    }
    
   
    /**
     * A generic test method to determine if the given session can call the delete method to result the expectation.  
     * @param session the session will call the isAuthorized method
     * @param pid the identifier of the object will be applied
     * @param permission the permission will be checked
     * @param expectedResult the expected for authorization. True will be successful.
     * @throws Exception
     */
    private void testIsAuthorized(Session session, Identifier pid, Permission permission, boolean expectedResult) throws Exception {
        if(expectedResult) {
            boolean result = MNodeService.getInstance(request).isAuthorized(session, pid, permission);
            assertTrue(result == expectedResult);
        } else {
            try {
                boolean result = MNodeService.getInstance(request).isAuthorized(session, pid, permission);
                fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
            } catch (NotAuthorized e) {
                
            }
        }
        
    }
    
    /**
     * A generic test method to determine if the given session can call the create method to result the expectation.  
     * @param session the session will call the create method
     * @param pid the identifier will be used at the create method
     * @param sysmeta the system metadata object will be used at the create method
     * @param expectedResult the expected result for authorization. True will be successful.
     */
    private Identifier testCreate (Session session, Identifier pid, SystemMetadata sysmeta, InputStream object, boolean expectedResult) throws Exception{
        if(expectedResult) {
            Identifier id = MNodeService.getInstance(request).create(session, pid, object, sysmeta);
            assertTrue(id.equals(pid));
        } else {
            try {
                pid = MNodeService.getInstance(request).create(session, pid, object, sysmeta);
                fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
            } catch (InvalidToken e) {
                
            }
        }
        return pid;
    }
    
    /**
     * A generic test method to determine if the given session can call the delete method to result the expectation.  
     * @param session the session will call the delete method
     * @param id the identifier will be used to call the delete method
     * @param expectedResult the expected result for authorization. True will be successful.
     */
     private void testDelete(Session session, Identifier pid, boolean expectedResult) throws Exception{
         if(expectedResult) {
             Identifier id = MNodeService.getInstance(request).delete(session, pid);
             assertTrue(id.equals(pid));
         } else {
             try {
                 pid = MNodeService.getInstance(request).delete(session, pid);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
    
     
     /**
      * A generic test method to determine if the given session can call the updateSystemMetadata method to result the expectation. 
      * @param session
      * @param pid
      * @param newSysmeta
      * @param expectedResult
      * @throws Exception
      */
     private void testUpdateSystemmetadata(Session session, Identifier pid, SystemMetadata newSysmeta, boolean expectedResult) throws Exception {
         if(expectedResult) {
             boolean result = MNodeService.getInstance(request).updateSystemMetadata(session, pid, newSysmeta);
             assertTrue(result == expectedResult);
         } else {
             try {
                 boolean result = MNodeService.getInstance(request).updateSystemMetadata(session, pid, newSysmeta);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
   
     
     /**
      *  A generic test method to determine if the given session can call the update method to result the expectation. 
      * @param session
      * @param pid
      * @param sysmeta
      * @param newPid
      * @param expectedResult
      * @throws Exception
      */
     private void testUpdate(Session session, Identifier pid, SystemMetadata sysmeta, Identifier newPid, boolean expectedResult) throws Exception {
         InputStream object = new ByteArrayInputStream(TEXT.getBytes("UTF-8"));
         if(expectedResult) {
             Identifier id = MNodeService.getInstance(request).update(session, pid, object, newPid, sysmeta);
             assertTrue(id.equals(newPid));
         } else {
             if(session == null) {
                 try {
                     Identifier id = MNodeService.getInstance(request).update(session, pid, object, newPid, sysmeta);
                     fail("we should get here since the previous statement should thrown an InvalidToken exception.");
                 } catch (InvalidToken e) {
                     
                 }
             } else {
                 try {
                     Identifier id = MNodeService.getInstance(request).update(session, pid, object, newPid, sysmeta);
                     fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
                 } catch (NotAuthorized e) {
                     
                 }
             }
         }
     }
     
     /**
      *  A generic test method to determine if the given session can call the archive method to result the expectation.
      * @param session
      * @param pid
      * @param expectedResult
      * @throws Exception
      */
     private void testArchive(Session session, Identifier pid ,boolean expectedResult) throws Exception {
         if(expectedResult) {
             Identifier id = MNodeService.getInstance(request).archive(session, pid);
             assertTrue(id.equals(pid));
         } else {
             try {
                 Identifier id = MNodeService.getInstance(request).archive(session, pid);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      *A generic test method to determine if the given session can call the publish method to result the expectation. 
      * @param session
      * @param originalIdentifier
      * @param expectedResult
      * @throws Exception
      */
     private void testPublish(Session session, Identifier originalIdentifier ,boolean expectedResult) throws Exception {
         if(expectedResult) {
             Identifier id = MNodeService.getInstance(request).publish(session, originalIdentifier);
             assertTrue(id.getValue().contains("doi:"));
         } else {
             try {
                 Identifier id = MNodeService.getInstance(request).publish(session, originalIdentifier);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the syncFailed method to result the expectation. 
      * @param session
      * @param syncFailed
      * @param expectedResult
      * @throws Exception
      */
     private void testSyncFailed(Session session, SynchronizationFailed syncFailed ,boolean expectedResult) throws Exception {
         if(expectedResult) {
             boolean success = MNodeService.getInstance(request).synchronizationFailed(session, syncFailed);
             assertTrue(success);
         } else {
             try {
                 boolean success = MNodeService.getInstance(request).synchronizationFailed(session, syncFailed);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the getReplica method to result the expectation. 
      * @param session
      * @param pid
      * @param expectedResult
      * @throws Exception
      */
     private void testGetReplica(Session session, Identifier pid ,boolean expectedResult) throws Exception {
         if(expectedResult) {
             InputStream input = MNodeService.getInstance(request).getReplica(session, pid);
             assertTrue(IOUtil.getInputStreamAsString(input).equals(TEXT));
         } else {
             try {
                 InputStream input = MNodeService.getInstance(request).getReplica(session, pid);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the systemmetadataChanged method to result the expectation.
      * @param session
      * @param pid
      * @param expectedResult
      * @throws Exception
      */
     private void testSystemmetadataChanged(Session session, Identifier pid ,boolean expectedResult) throws Exception {
         Date dateSysMetaLastModified = new Date();
         long serialVersion =200;
         if(expectedResult) {
             MNodeService.getInstance(request).systemMetadataChanged(session, pid, serialVersion, dateSysMetaLastModified);
         } else {
             try {
                 MNodeService.getInstance(request).systemMetadataChanged(session, pid, serialVersion, dateSysMetaLastModified);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
 
     
     /**
      * A generic test method to determine if the given session can call the getLogRecords method to result the expectation. 
      * @param session
      * @param expectedResult
      * @throws Exception
      */
     private void testGetLogRecords(Session session,boolean expectedResult) throws Exception {
         SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
         Date fromDate = sdf.parse("1971-01-01");
         Date toDate = new Date();
         String event = null;
         String pidFilter = null;
         int start = 0;
         int count = 1;
         if(expectedResult) {
             Log log = MNodeService.getInstance(request).getLogRecords(session, fromDate, toDate, event, pidFilter, start, count);
             assertTrue(log.getCount() > 1);
         } else {
             try {
                 MNodeService.getInstance(request).getLogRecords(session, fromDate, toDate, event, pidFilter, start, count);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the getLogRecords method to result the expectation. 
      * @param session
      * @param expectedResult
      * @throws Exception
      */
     private Identifier testGenerateIdentifier(Session session,String scheme, String fragment, boolean expectedResult) throws Exception {
         Identifier id  = null;
         if(expectedResult) {
             id= MNodeService.getInstance(request).generateIdentifier(session, scheme, fragment);
             assertTrue(id.getValue() != null && !id.getValue().trim().equals(""));
         } else {
             try {
                 MNodeService.getInstance(request).generateIdentifier(session, scheme, fragment);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (InvalidToken e) {
                 
             }
         }
         return id;
     }
     
     /**
      * Test the get api methods (describe, getSystemmetadata, get, view, getPackage, getChecksum)
      * @param session
      * @param id
      * @param expectedSum
      * @param expectedResult
      * @throws Exception
      */
     private void testGetAPI(Session session, Identifier id, Checksum expectedSum, boolean expectedResult) throws Exception {
         testDescribe(session, id, expectedResult);
         testGetSystemmetadata(session, id, expectedResult);
         testGet(session, id, expectedResult);
         testView(session, id, "metacatui", expectedResult);
         testGetPackage(session, id, expectedResult);
         testGetChecksum(session, id, expectedSum, expectedResult);
     }
     
     /**
      * A generic test method to determine if the given session can call the describe method to result the expection.  
      * @param session the session will call the describe method
      * @param id the identifier will be used to call the describe method
      * @param expectedResult the expected result for authorization. True will be successful.
      */
     private void testDescribe(Session session, Identifier id, boolean expectedResult) throws Exception {
         if(expectedResult) {
             DescribeResponse reponse =MNodeService.getInstance(request).describe(session,id);
             ObjectFormatIdentifier format = reponse.getDataONE_ObjectFormatIdentifier();
             assertTrue(format.getValue().equals("application/octet-stream"));
         } else {
             try {
                 DescribeResponse reponse =MNodeService.getInstance(request).describe(session,id);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the getSystemMetadata method to result the expectation.  
      * @param session the session will call the getSystemMetadata method
      * @param id the identifier will be used to call the getSystemMetadata method
      * @param expectedResult the expected result for authorization. True will be successful.
      */
     private void testGetSystemmetadata(Session session, Identifier id, boolean expectedResult) throws Exception {
         if(expectedResult) {
             SystemMetadata sysmeta =MNodeService.getInstance(request).getSystemMetadata(session,id);
             assertTrue(sysmeta.getIdentifier().equals(id));
         } else {
             try {
                 SystemMetadata sysmeta =MNodeService.getInstance(request).getSystemMetadata(session,id);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the get method to result the expectation.  
      * @param session the session will call the get method
      * @param id the identifier will be used to call the get method
      * @param expectedResult the expected result for authorization. True will be successful.
      */
     private void testGet(Session session, Identifier id, boolean expectedResult) throws Exception {
         if(expectedResult) {
             InputStream out =MNodeService.getInstance(request).get(session,id);
             assertTrue(IOUtil.getInputStreamAsString(out).equals(TEXT));
             out.close();
         } else {
             try {
                 InputStream out =MNodeService.getInstance(request).get(session,id);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the getPackage method to result the expectation.
      * @param session
      * @param pid
      * @param expectedResult
      * @throws Exception
      */
     private void testGetPackage(Session session, Identifier pid ,boolean expectedResult) throws Exception {
         ObjectFormatIdentifier formatId = new ObjectFormatIdentifier();
         formatId.setValue("application/bagit-097");
         if(expectedResult) {
             try {
                 MNodeService.getInstance(request).getPackage(session, formatId, pid);
                 fail("we should get here since the previous statement should thrown an Invalid exception.");
             } catch (InvalidRequest e) {
                 assertTrue(e.getMessage().contains("is not a package"));
             }
         } else {
             try {
                 MNodeService.getInstance(request).getPackage(session, formatId, pid);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the getChecksum method to result the expectation. 
      * @param session
      * @param pid
      * @param expectedValue
      * @param expectedResult
      * @throws Exception
      */
     private void testGetChecksum(Session session, Identifier pid, Checksum expectedValue, boolean expectedResult) throws Exception {
         if(expectedResult) {
            Checksum checksum= MNodeService.getInstance(request).getChecksum(session, pid, ALGORITHM);
            //System.out.println("$$$$$$$$$$$$$$$$$$$$$The chechsum from MN is "+checksum.getValue());
            //System.out.println("The exprected chechsum is "+expectedValue.getValue());
            assertTrue(checksum.getValue().equals(expectedValue.getValue()));
         } else {
             try {
                 Checksum checksum= MNodeService.getInstance(request).getChecksum(session, pid, ALGORITHM);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     
     /**
      * A generic test method to determine if the given session can call the view method to result the expectation. 
      * @param session
      * @param pid
      * @param theme
      * @param expectedResult
      * @throws Exception
      */
     private void testView(Session session, Identifier pid, String theme, boolean expectedResult) throws Exception {
         if(expectedResult) {
             InputStream input = MNodeService.getInstance(request).view(session, theme, pid);
             assertTrue(IOUtil.getInputStreamAsString(input).equals(TEXT));
             input.close();
         } else {
             try {
                 InputStream input = MNodeService.getInstance(request).view(session, theme, pid);
                 fail("we should get here since the previous statement should thrown an NotAuthorized exception.");
             } catch (NotAuthorized e) {
                 
             }
         }
     }
     


     /**
      * Just test we can get the node capacity since there is not session requirement
      * @throws Exception
      */
     private void testGetCapacity() throws Exception {
         Node node = MNodeService.getInstance(request).getCapabilities();
         assertTrue(node.getName().equals(Settings.getConfiguration().getString("dataone.nodeName")));
     }
     
     
     /**
      * Test the listObjects method. It doesn't need any authorization. 
      * So the public user and the node subject should get the same total.
      * @throws Exception
      */
     private void testListObjects() throws Exception {
         Session publicSession =getPublicUser();
         SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
         Date startTime = sdf.parse("1971-01-01");
         Date endTime = new Date();
         ObjectFormatIdentifier objectFormatId = null;
         boolean replicaStatus = false;
         Identifier identifier = null;
         int start = 0;
         int count = 1;
         ObjectList publicList = MNodeService.getInstance(request).listObjects(publicSession, startTime, endTime, objectFormatId, identifier, replicaStatus, start, count);
         int publicSize = publicList.getTotal();
         Session nodeSession = getMNSession();
         ObjectList privateList = MNodeService.getInstance(request).listObjects(nodeSession, startTime, endTime, objectFormatId, identifier, replicaStatus, start, count);
         int privateSize = privateList.getTotal();
         assertTrue(publicSize == privateSize);
     }
     
     /**
      * Test the listObjects method. It doesn't need any authorization. 
      * @throws Exception
      */
     private void testListViews() throws Exception {
         OptionList publicList = MNodeService.getInstance(request).listViews();
         assertTrue(publicList.sizeOptionList() > 0);
     }
    
    /**
     *Get a user who is in the knb data admin group. It is Lauren Walker.
     *It also includes the subject information from the cn.
     */
    public static Session getOneKnbDataAdminsMemberSession() throws Exception {
        Session session = new Session();
        Subject subject = new Subject();
        subject.setValue(KNBAMDINMEMBERSUBJECT);
        session.setSubject(subject);
        SubjectInfo subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
        session.setSubjectInfo(subjectInfo);
        return session;
    }
    
    /**
     * Get the subject of a user who is in the knb data admin group. It is Lauren Walker.
     * @return
     */
    public static Subject getOneKnbDataAdminsMemberSubject() {
        Subject subject = new Subject();
        subject.setValue(KNBAMDINMEMBERSUBJECT);
        return subject;
    }
    
    /**
     *Get the subject of the knb data admin group
     */
    public static Subject getKnbDataAdminsGroupSubject() {
        Subject subject = new Subject();
        subject.setValue("CN=knb-data-admins,DC=dataone,DC=org");
        return subject;
    }
    
    /**
     *Get a user who is in the PISCO-data-managers.
     *It also includes the subject information from the cn.
     */
    public static Session getOnePISCODataManagersMemberSession() throws Exception {
        Session session = new Session();
        Subject subject = new Subject();
        subject.setValue(PISCOMANAGERMEMBERSUBJECT);
        session.setSubject(subject);
        SubjectInfo subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
        session.setSubjectInfo(subjectInfo);
        return session;
    }
    
    /**
     * Get the subject of a user who is in the PISCO-data-managers group. 
     * @return
     */
    public static Subject getOnePISCODataManagersMemberSubject() throws Exception {
        Subject subject = new Subject();
        subject.setValue(PISCOMANAGERMEMBERSUBJECT);
        return subject;
    }
    
    /**
     *Get the subject of the PISCO-data-managers group
     */
    public static Subject getPISCODataManagersGroupSubject() {
        Subject subject = new Subject();
        subject.setValue("CN=PISCO-data-managers,DC=dataone,DC=org");
        return subject;
    }
    
  
    
    /**
     * Get the session with the user public
     */
    public static Session getPublicUser() {
        Session session = new Session();
        Subject subject = new Subject();
        subject.setValue(Constants.SUBJECT_PUBLIC);
        session.setSubject(subject);
        return session;
    }
    
    /**
     * Get the session for the third user.
     * @return
     */
    public static Session getThirdUser() {
        Session session = new Session();
        Subject subject = new Subject();
        subject.setValue("cn=test3,o=NCEAS,dc=dataone,dc=org");
        session.setSubject(subject);
        return session;
    }

}
