/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2019.                            (c) 2019.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 ************************************************************************
 */

package org.opencadc.raven;

import ca.nrc.cadc.net.HttpDelete;
import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.net.HttpPost;
import ca.nrc.cadc.net.HttpUpload;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.util.MultiValuedProperties;
import ca.nrc.cadc.util.PropertiesReader;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.Subject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.opencadc.inventory.Artifact;
import org.opencadc.inventory.SiteLocation;
import org.opencadc.inventory.StorageSite;
import org.opencadc.inventory.db.ArtifactDAO;
import org.opencadc.inventory.db.StorageSiteDAO;
import org.opencadc.raven.PostAction;

/**
 * Test transfer negotiation.
 * 
 * @author majorb
 */
public class NegotiationTest extends RavenTest {
    
    private static final Logger log = Logger.getLogger(NegotiationTest.class);
    
    static {
        Log4jInit.setLevel("org.opencadc.raven", Level.INFO);
        Log4jInit.setLevel("ca.nrc.cadc.db", Level.DEBUG);
    }
    
    public NegotiationTest() throws Exception {
        super();
    }
    
    @Test
    public void testGetAllCopies() {
        try {
            
            System.setProperty(PropertiesReader.CONFIG_DIR_SYSTEM_PROPERTY, "build/resources/test");
            
            Subject.doAs(userSubject, new PrivilegedExceptionAction<Object>() {
                public Object run() throws Exception {
                    
                    URI resourceID1 = URI.create("ivo://negotiation-test-site1");
                    URI resourceID2 = URI.create("ivo://negotiation-test-site2");
                    
                    ArtifactDAO artifactDAO = new ArtifactDAO();
                    artifactDAO.setConfig(config);
                    StorageSiteDAO siteDAO = new StorageSiteDAO();
                    siteDAO.setConfig(config);
                    StorageSite site1 = new StorageSite(resourceID1, "site1");
                    StorageSite site2 = new StorageSite(resourceID2, "site2");

                    URI artifactURI = URI.create("cadc:TEST/" + UUID.randomUUID() + ".fits");
                    URI checksum = URI.create("md5:testvalue");
                    Artifact artifact = new Artifact(artifactURI, checksum, new Date(), 1L);

                    try {
                        siteDAO.put(site1);
                        siteDAO.put(site2);
                        
                        SiteLocation location1 = new SiteLocation(site1.getID());
                        SiteLocation location2 = new SiteLocation(site2.getID());
                        
                        Protocol protocol = new Protocol(VOS.PROTOCOL_HTTPS_GET);
                        Transfer transfer = new Transfer(
                            artifactURI, Direction.pullFromVoSpace, Arrays.asList(protocol));
                        transfer.version = VOS.VOSPACE_21;
                        
                        artifactDAO.put(artifact);
                        
                        // test that there are no copies available
                        try {
                            negotiate(transfer);
                            Assert.fail("should have received file not found exception");
                        } catch (FileNotFoundException e) {
                            // expected
                        }
                        
                        artifact.siteLocations.add(location1);
                        artifactDAO.put(artifact, true);
                        
                        // test that there's one copy
                        Transfer response = negotiate(transfer);
                        Assert.assertEquals(1, response.getAllEndpoints().size());
                        
                        artifact.siteLocations.add(location2);
                        artifactDAO.put(artifact, true);
                        
                        // test that there are now two copies
                        response = negotiate(transfer);
                        Assert.assertEquals(2, response.getAllEndpoints().size());
                        
                        return null;
                        
                    } finally {
                        // cleanup sites
                        siteDAO.delete(site1.getID());
                        siteDAO.delete(site2.getID());
                        artifactDAO.delete(artifact.getID());
                    }
                }
            });
            
        } catch (Throwable t) {
            log.error("unexpected throwable", t);
            Assert.fail("unexpected throwable: " + t);
        } finally {
            System.clearProperty(PropertiesReader.CONFIG_DIR_SYSTEM_PROPERTY);
        }
    }
    
}
