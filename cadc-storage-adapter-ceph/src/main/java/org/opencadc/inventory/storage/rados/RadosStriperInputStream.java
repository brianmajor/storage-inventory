
/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2020.                            (c) 2020.
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
 *
 ************************************************************************
 */

package org.opencadc.inventory.storage.rados;

import com.ceph.rados.IoCTX;
import com.ceph.rados.exceptions.RadosException;
import com.ceph.rados.exceptions.RadosNotFoundException;
import com.ceph.radosstriper.IoCTXStriper;
import com.ceph.radosstriper.RadosStriper;

import java.io.IOException;

import org.apache.log4j.Logger;


/**
 * Creates an InputStream for the underlying IO Context from RADOS.  Since RADOS wants to only write out Byte Arrays,
 * we need to use that as a data source to this Input Stream.
 * Note that the main read() method is not implemented here as it relies on the RADOS API to populate the underlying
 * buffer array, which relies on the buffer array being passed in.
 *
 * <p>This implementation uses the RADOS Striper API to chunk up large files.  The default configuration of a Ceph cluster
 * limits files to 128MB when using the basic RADOS API, so the Striper API will use the underlying striping logic to
 * break up the file.
 * jenkinsd 2019.12.11
 */
public class RadosStriperInputStream extends RadosInputStream {

    private static final Logger LOGGER = Logger.getLogger(RadosStriperInputStream.class);

    public RadosStriperInputStream(final RadosStriper rados, final String objectID) {
        super(rados, objectID);
    }

    /**
     * Read bytes from the RADOS back end.
     *
     * @param ioCTX The IO Context connection.
     * @param b     The byte array to read into.
     * @param len   The amount of bytes to read.
     * @return Count of bytes read.
     *
     * @throws IOException For any backend reading.
     */
    @Override
    int readRadosBytes(IoCTX ioCTX, byte[] b, int len) throws IOException {
        try (final IoCTXStriper ioCTXStriper = ((RadosStriper) rados).ioCtxCreateStriper(ioCTX)) {
            return ioCTXStriper.read(objectID, len, position, b);
        } catch (Exception e) {
            if (e instanceof RadosNotFoundException) {
                throw (RadosNotFoundException) e;
            } else if (e instanceof RadosException) {
                throw (RadosException) e;
            } else if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                LOGGER.warn("Unable to close the IO RADOS Context.", e);
                return -1;
            }
        }
    }
}
