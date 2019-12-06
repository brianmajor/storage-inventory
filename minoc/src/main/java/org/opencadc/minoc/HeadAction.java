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

package org.opencadc.minoc;

import java.net.URI;

import org.apache.log4j.Logger;
import org.opencadc.inventory.Artifact;
import org.opencadc.inventory.InventoryUtil;
import org.opencadc.inventory.db.ArtifactDAO;
import org.opencadc.minoc.ArtifactUtil.HttpMethod;

/**
 * Interface with storage and inventory to get the metadata of an artifact.
 *
 * @author majorb
 */
public class HeadAction extends ArtifactAction {
    
    private static final Logger log = Logger.getLogger(HeadAction.class);

    /**
     * Default, no-arg constructor.
     */
    public HeadAction() {
        super(HttpMethod.HEAD);
    }
    
    /**
     * Constructor for subclass, GetAction
     * @param method
     */
    public HeadAction(HttpMethod method) {
        super(method);
    }

    /**
     * Download the artifact or cutouts of the artifact.
     * @param artifactURI The identifier for the artifact. 
     */
    @Override
    public Artifact execute(URI artifactURI) throws Exception {
        ArtifactDAO dao = new ArtifactDAO();
        Artifact artifact = getArtifact(artifactURI, dao);

        String filename = InventoryUtil.computeArtifactFilename(artifactURI);
        URI contentChecksum = artifact.getContentChecksum();
        Long contentLength = artifact.getContentLength();
        String contentEncoding = artifact.contentEncoding;
        String contentType = artifact.contentType;
        log.debug("Content-MD5: " + contentChecksum.getSchemeSpecificPart());
        log.debug("Content-Length: " + contentLength);
        log.debug("Content-Encoding: " + contentEncoding);
        log.debug("Content-Type: " + contentType);

        syncOutput.setHeader("Content-Disposition", "attachment; filename=" + filename);
        syncOutput.setHeader("Content-MD5", contentChecksum.getSchemeSpecificPart());
        syncOutput.setHeader("Content-Length", contentLength);
        syncOutput.setHeader("Content-Encoding", contentEncoding);
        syncOutput.setHeader("Content-Type", contentType);
        return artifact;    
    }

}