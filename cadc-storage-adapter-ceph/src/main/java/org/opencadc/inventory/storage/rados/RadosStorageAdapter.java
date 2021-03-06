
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

import ca.nrc.cadc.io.ByteCountInputStream;
import ca.nrc.cadc.io.ReadException;
import ca.nrc.cadc.io.WriteException;
import ca.nrc.cadc.net.IncorrectContentChecksumException;
import ca.nrc.cadc.net.IncorrectContentLengthException;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.StringUtil;
import com.ceph.rados.IoCTX;
import com.ceph.rados.ListCtx;
import com.ceph.rados.Rados;
import com.ceph.rados.exceptions.RadosException;
import com.ceph.rados.exceptions.RadosNotFoundException;
import com.ceph.rados.jna.RadosObjectInfo;
import com.ceph.radosstriper.IoCTXStriper;
import com.ceph.radosstriper.RadosStriper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.util.ArrayDataOutput;
import nom.tam.util.BufferedDataOutputStream;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.opencadc.inventory.StorageLocation;
import org.opencadc.inventory.storage.NewArtifact;
import org.opencadc.inventory.storage.StorageAdapter;
import org.opencadc.inventory.storage.StorageEngageException;
import org.opencadc.inventory.storage.StorageMetadata;


/**
 * Implementation of a Storage Adapter using the Ceph RADOS API.
 */
public class RadosStorageAdapter implements StorageAdapter {

    private static final Logger LOGGER = Logger.getLogger(RadosStorageAdapter.class);

    private static final String META_POOL_NAME = "default.rgw.meta";
    private static final String META_NAMESPACE = "root";
    static final String STORAGE_ID_URI_TEMPLATE = "rados:%s";
    static final String CHECKSUM_URI_TEMPLATE = "md5:%s";

    private static final String DATA_POOL_NAME = "default.rgw.buckets.non-ec";
    private static final String BUCKET_NAME_LOOKUP = ".bucket.meta.%s";
    private static final String OBJECT_ID_LOOKUP = "%s_%s";

    private static final String DIGEST_ALGORITHM = "MD5";
    private static final int DEFAULT_BUCKET_HASH_LENGTH = 5;
    private static final int BUFFER_SIZE_BYTES = 1024 * 1024; // One Megabyte.
    private static final int DEFAULT_LIST_PAGE_SIZE = 1000;

    private final String cephxID;
    private final String clusterName;

    private final Rados radosClient;
    private final RadosStriper radosStriperClient;


    /**
     * New RADOS adapter.
     *
     * @param userID      User ID to connect with.  This may be temporary until we have a dedicated account.
     * @param clusterName The cluster to connect to.
     */
    public RadosStorageAdapter(final String userID, final String clusterName) {
        this.cephxID = String.format("client.%s", userID);
        this.clusterName = clusterName;

        try {
            this.radosClient = connect();
            this.radosStriperClient = connectStriper();
        } catch (RadosException e) {
            // Can't connect, so no further action can be taken.
            throw new RuntimeException(e);
        }

        LOGGER.setLevel(Level.DEBUG);
    }

    /**
     * Create a new client. Override this to Mock test, if needed.
     *
     * @return An instance of Rados. Never null.
     */
    Rados createRadosClient() {
        return new Rados(clusterName, cephxID, Rados.OPERATION_NOFLAG);
    }

    private RadosStriper connectStriper() throws RadosException {
        final RadosStriper radosStriper = new RadosStriper(clusterName, cephxID, Rados.OPERATION_NOFLAG);
        radosStriper.confReadFile(
                new File(String.format("%s/.ceph/%s.conf", System.getProperty("user.home"), clusterName)));
        LOGGER.debug(String.format("Connecting to Ceph OSD at %s...", radosStriper.confGet("mon_host")));
        radosStriper.connect();
        LOGGER.debug(String.format("Connected to Ceph OSD at %s as %s.", radosStriper.confGet("mon_host"), cephxID));
        return radosStriper;
    }

    private Rados connect() throws RadosException {
        final Rados rados = createRadosClient();
        rados.confReadFile(new File(String.format("%s/.ceph/%s.conf", System.getProperty("user.home"), clusterName)));

        LOGGER.debug(String.format("Connecting to Ceph OSD at %s...", rados.confGet("mon_host")));
        rados.connect();
        LOGGER.debug(String.format("Connected to Ceph OSD at %s as %s.", rados.confGet("mon_host"), cephxID));
        return rados;
    }

    private MessageDigest createDigester() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to proceed with checksum implementation.", e);
        }
    }

    private URI createChecksum(final MessageDigest messageDigest) {
        return URI.create(String.format("%s:%s", messageDigest.getAlgorithm().toLowerCase(),
                                        new BigInteger(1, messageDigest.digest()).toString(16)));
    }

    private IoCTX contextConnect(final Rados client, final String poolName) throws RadosException {
        try {
            return client.ioCtxCreate(poolName);
        } catch (IOException e) {
            throw new RadosException(e.getMessage(), e);
        }
    }

    private IoCTX contextConnect(final String poolName) throws RadosException {
        try {
            return contextConnect(radosClient, poolName);
        } catch (IOException e) {
            throw new RadosException(e.getMessage(), e);
        }
    }

    private IoCTXStriper contextConnectStriper(final String poolName) throws RadosException {
        try {
            return radosStriperClient.ioCtxCreateStriper(contextConnect(poolName));
        } catch (IOException e) {
            throw new RadosException(e.getMessage(), e);
        }
    }

    private String getObjectID(final StorageLocation storageLocation) {
        return storageLocation.getStorageID().getSchemeSpecificPart();
    }

    URI generateStorageID() {
        return URI.create(String.format(STORAGE_ID_URI_TEMPLATE, UUID.randomUUID().toString()));
    }

    /**
     * Reusable way to create a StorageMetadata object.
     *
     * @param storageID     The URI storage ID.
     * @param artifactURI   The Artifact URI as it is in the inventory system.
     * @param md5           The MD5 URI.
     * @param contentLength The content length in bytes.
     * @return StorageMetadata instance; never null.
     */
    StorageMetadata toStorageMetadata(final URI storageID, final URI artifactURI,
                                      final URI md5, final long contentLength) {
        final StorageLocation storageLocation = new StorageLocation(storageID);

        final StorageMetadata storageMetadata = new StorageMetadata(storageLocation, md5, contentLength);
        storageMetadata.artifactURI = artifactURI;

        return storageMetadata;
    }

    @Override
    public void get(StorageLocation storageLocation, OutputStream outputStream) throws ResourceNotFoundException,
                                                                                       ReadException, WriteException,
                                                                                       StorageEngageException,
                                                                                       TransientException {
        try (final InputStream inputStream = createInputStream(getObjectID(storageLocation))) {
            final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (RadosNotFoundException e) {
            throw new ResourceNotFoundException(e.getMessage(), e);
        } catch (RadosException e) {
            throw new StorageEngageException(e.getMessage(), e);
        } catch (IOException e) {
            throw new ReadException(e.getMessage(), e);
        }
    }

    @Override
    public void get(StorageLocation storageLocation, OutputStream outputStream, Set<String> cutouts)
            throws ResourceNotFoundException, ReadException, WriteException, StorageEngageException,
                   TransientException {
        if ((cutouts == null) || cutouts.isEmpty()) {
            get(storageLocation, outputStream);
        } else {
            final long start = System.currentTimeMillis();

            try (final InputStream inputStream = createStriperInputStream(getObjectID(storageLocation))) {
                final Fits fitsFile = new Fits(inputStream);
                final ArrayDataOutput dataOutput = new BufferedDataOutputStream(outputStream);

                // Just get the first cutout for now.
                BasicHDU<?> hdu;
                long beforeHDU = System.currentTimeMillis();
                int count = 1;
                while ((hdu = fitsFile.readHDU()) != null) {
                    final Header header = hdu.getHeader();
                    final long afterReadHDU = System.currentTimeMillis();
                    final HeaderCard headerNameCard = header.findCard("EXTNAME");
                    LOGGER.debug(String.format("%d,\"%s\",%d,\"milliseconds\"", count,
                                               headerNameCard == null ? "N/A" : headerNameCard.getValue(),
                                               afterReadHDU - beforeHDU));
                    beforeHDU = System.currentTimeMillis();
                    if (hdu.getAxes() != null) {
                        final int axesCount = hdu.getAxes().length;
                        for (int i = 0; i < axesCount; i++) {
                            header.findCard(String.format("NAXIS%d", i + 1)).setValue(0);
                        }
                    }

                    header.write(dataOutput);
                    dataOutput.write(new short[0]);
                    count++;
                }
            } catch (RadosNotFoundException e) {
                throw new ResourceNotFoundException(e.getMessage(), e);
            } catch (RadosException e) {
                throw new StorageEngageException(e.getMessage(), e);
            } catch (FitsException | IOException e) {
                throw new ReadException("Unable to process FITS file.", e);
            }
            LOGGER.debug(String.format("Read and wrote HDUs in %d milliseconds.", System.currentTimeMillis() - start));
        }
    }

    void writeStream(final String objectID, final InputStream byteCountInputStream) throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE_BYTES];

        int bytesRead;
        long offset = 0L;
        while ((bytesRead = byteCountInputStream.read(buffer)) >= 0) {
            try (final IoCTX ioCTX = contextConnect(DATA_POOL_NAME)) {
                ioCTX.write(objectID, buffer, offset);
            }
            offset += bytesRead;
        }
    }

    @Override
    public StorageMetadata put(NewArtifact newArtifact, InputStream inputStream)
            throws IncorrectContentChecksumException, IncorrectContentLengthException, WriteException,
                   StorageEngageException, TransientException {
        try {
            final URI artifactURI = newArtifact.getArtifactURI();
            final URI storageID = generateStorageID();
            final String objectID = storageID.getSchemeSpecificPart();

            final DigestInputStream digestInputStream = new DigestInputStream(inputStream, createDigester());
            final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(digestInputStream);

            writeStream(objectID, byteCountInputStream);

            LOGGER.debug(String.format("Wrote %d bytes to %s.", byteCountInputStream.getByteCount(), objectID));
            final URI expectedChecksum = newArtifact.contentChecksum;
            final URI calculatedChecksum = createChecksum(digestInputStream.getMessageDigest());

            if (expectedChecksum == null) {
                LOGGER.debug("No checksum provided.  Defaulting the calculated one.");
            } else if (!expectedChecksum.equals(calculatedChecksum)) {
                throw new IncorrectContentChecksumException(
                        String.format("Checksums do not match.  Expected %s but was %s.",
                                      expectedChecksum.toString(),
                                      calculatedChecksum.toString()));
            }

            final Long expectedContentLength = newArtifact.contentLength;
            final long calculatedContentLength = byteCountInputStream.getByteCount();

            if (expectedContentLength == null) {
                LOGGER.debug("No content length provided.  Defaulting the calculated one.");
            } else if (expectedContentLength != calculatedContentLength) {
                throw new IncorrectContentLengthException(
                        String.format("Content lengths do not match.  Expected %d but was %d.", expectedContentLength,
                                      calculatedContentLength));
            }

            try (final IoCTX ioCTX = contextConnect(DATA_POOL_NAME)) {
                ioCTX.setExtendedAttribute(objectID, "uri",
                                           newArtifact.getArtifactURI().toASCIIString().trim());
                ioCTX.setExtendedAttribute(objectID, "md5", calculatedChecksum.toString());
            }

            return toStorageMetadata(storageID, artifactURI, calculatedChecksum, calculatedContentLength);
        } catch (RadosException e) {
            throw new StorageEngageException(e.getMessage(), e);
        } catch (IOException e) {
            throw new WriteException(e.getMessage(), e);
        } catch (Exception e) {
            throw new TransientException(e.getMessage(), e);
        }
    }

    /**
     * Delete from storage the artifact identified by storageLocation.
     *
     * @param storageLocation Identifies the artifact to delete.
     * @throws ResourceNotFoundException If the artifact could not be found.
     * @throws IOException               If an unrecoverable error occurred.
     * @throws StorageEngageException    If the adapter failed to interact with storage.
     * @throws TransientException        If an unexpected, temporary exception occurred.
     */
    public void delete(StorageLocation storageLocation)
            throws ResourceNotFoundException, IOException, StorageEngageException, TransientException {
        try (final IoCTXStriper ioCTX = contextConnectStriper(DATA_POOL_NAME)) {
            ioCTX.remove(getObjectID(storageLocation));
        } catch (RadosNotFoundException e) {
            throw new ResourceNotFoundException(e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    StorageMetadata head(final String storageBucket, final String objectID) {
        try (IoCTX ioCTX = contextConnect(DATA_POOL_NAME)) {
            final RadosObjectInfo radosObjectInfo = ioCTX.stat(objectID);
            final Map<String, String> extendedAttributes = ioCTX.getExtendedAttributes(objectID);
            final String artifactURIMetadataValue = extendedAttributes.get("uri");
            final URI metadataArtifactURI = StringUtil.hasLength(artifactURIMetadataValue)
                                            ? URI.create(artifactURIMetadataValue)
                                            : URI.create(String.format("%s:%s/%s", "UNKNOWN", storageBucket, objectID));
            final String md5ChecksumValue = extendedAttributes.get("md5");
            final URI md5 = URI.create(String.format("%s:%s", "md5", StringUtil.hasLength(md5ChecksumValue)
                                                                     ? md5ChecksumValue
                                                                     : "UNKNOWN"));
            return new StorageMetadata(new StorageLocation(metadataArtifactURI), md5, radosObjectInfo.getSize());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Obtain an Iterator that contains a set size of items.  The internal listing mechanism from RADOS will be used to
     * obtain a page of data when needed via the <code>fillBuffer()</code> method.
     *
     * @param storageBucket The bucket to match against.
     * @param pageSize      The page size of data.
     * @return Iterator of StorageMetadata objects.  Never null.
     *
     * @throws IOException If any Ceph interaction fails.
     */
    Iterator<StorageMetadata> pageIterator(final String storageBucket, final Integer pageSize) throws IOException {
        //final String bucket = lookupBucketMarker(storageBucket);
        final Optional<Integer> optionalPageSize = Optional.ofNullable(pageSize);

        try (final IoCTX ioCTX = contextConnect(DATA_POOL_NAME)) {
            final int configuredPageSize = optionalPageSize.orElse(DEFAULT_LIST_PAGE_SIZE);

            return new Iterator<StorageMetadata>() {
                private final ListCtx listCtx = ioCTX.listObjectsPartial(configuredPageSize);
                //private String[] buffer = Arrays.stream(listCtx.getObjects()).filter(
                //        val -> !StringUtil.hasLength(bucket) || val.startsWith(bucket)).toArray(String[]::new);
                private String[] buffer = listCtx.getObjects();
                private int bufferPosition = 0;

                @Override
                public boolean hasNext() {
                    return bufferPosition < buffer.length || fillBuffer() > 0;
                }

                @Override
                public StorageMetadata next() {
                    return head(storageBucket, buffer[bufferPosition++]);
                }

                int fillBuffer() {
                    try {
                        final int objectCount = listCtx.nextObjects();
                        final int bufferCount;
                        if (objectCount > 0) {
                            LOGGER.debug(String.format("Read in %d objects.", objectCount));
                            buffer = listCtx.getObjects();
                            //buffer = Arrays.stream(listCtx.getObjects()).filter(
                            //        val -> !StringUtil.hasLength(bucket) || val.startsWith(bucket)).toArray(
                            //        String[]::new);
                            if (buffer.length == 0) {
                                LOGGER.debug("No objects match.  Fetching more results.");
                                bufferCount = fillBuffer();
                            } else {
                                bufferPosition = 0;
                                bufferCount = buffer.length;
                            }
                        } else {
                            LOGGER.debug("No more objects to read.");
                            bufferCount = 0;
                        }

                        return bufferCount;
                    } catch (IOException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
            };
        }
    }

    /**
     * Iterator of items ordered by their storageIDs.
     *
     * @return An iterator over an ordered list of items in storage.
     *
     * @throws StorageEngageException If the adapter failed to interact with storage.
     * @throws TransientException     If an unexpected, temporary exception occurred.
     */
    public Iterator<StorageMetadata> iterator()
            throws StorageEngageException, TransientException {
        return iterator(null);
    }

    /**
     * Iterator of items ordered by their storageIDs in the given bucket.
     *
     * @param storageBucket Only iterate over items in this bucket.
     * @return An iterator over an ordered list of items in this storage bucket.
     *
     * @throws StorageEngageException If the adapter failed to interact with storage.
     * @throws TransientException     If an unexpected, temporary exception occurred.
     */
    public Iterator<StorageMetadata> iterator(String storageBucket)
            throws StorageEngageException, TransientException {
        try {
            return new RadosStorageMetadataIterator(this, storageBucket);
        } catch (IOException e) {
            throw new StorageEngageException(e.getMessage(), e);
        }
    }

    /**
     * Get set of items in the given bucket.
     *
     * @param storageBucket Only iterate over items in this bucket.
     * @return An iterator over an ordered list of items in this storage bucket.
     *
     * @throws StorageEngageException If the adapter failed to interact with storage.
     * @throws TransientException     If an unexpected, temporary exception occurred.
     */
    public SortedSet<StorageMetadata> list(String storageBucket)
            throws StorageEngageException, TransientException {
        SortedSet<StorageMetadata> ret = new TreeSet<StorageMetadata>();
        Iterator<StorageMetadata> i = iterator(storageBucket);
        while (i.hasNext()) {
            ret.add(i.next());
        }
        return ret;
    }

    private InputStream createStriperInputStream(final String objectID) throws IOException {
        return new RadosStriperInputStream(radosStriperClient, objectID);
    }

    private InputStream createInputStream(final String objectID) throws IOException {
        return new RadosInputStream(radosClient, objectID);
    }
}
