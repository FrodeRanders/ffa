package se.fk.mimer.pipeline;

import se.fk.mimer.runtime.Dataleverans;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/** S3-kompatibel lagring, testad mot RustFS. Ett befintligt id får aldrig skrivas över. */
public final class Objektlager implements AutoCloseable {
    private final S3Client s3;
    private final String bucket;

    public Objektlager(String endpoint, String accessKey, String secretKey, String bucket) {
        this.bucket = bucket;
        s3 = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(20)))
                .build();
    }

    public void initiera() {
        try { s3.createBucket(b -> b.bucket(bucket)); }
        catch (S3Exception e) {
            if (!"BucketAlreadyOwnedByYou".equals(e.awsErrorDetails().errorCode())) throw e;
        }
    }

    public void lagra(Dataleverans d) {
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(d.id().toString())
                    .ifNoneMatch("*").contentType("application/json").metadata(Leveransformat.metadata(d)).build(),
                    RequestBody.fromBytes(d.dokument().json()));
        } catch (S3Exception e) {
            if (e.statusCode() != 412 && e.statusCode() != 409) throw e;
            var existing = las(d.id());
            if (existing == null) throw e;
            if (!Leveransformat.samma(existing, d))
                throw new IllegalArgumentException("Objektnyckeln är redan knuten till annan leveransdata", e);
        }
    }

    public Dataleverans las(UUID id) {
        try {
            var object = s3.getObjectAsBytes(b -> b.bucket(bucket).key(id.toString()));
            var delivery = Leveransformat.franObjekt(object.asByteArray(), object.response().metadata());
            if (!id.equals(delivery.id())) throw new IllegalStateException("Objektets nyckel och metadata skiljer sig");
            return delivery;
        } catch (NoSuchKeyException e) { return null; }
    }

    /** Endast teststädning: anropas inte av leveransflödet. */
    public void radera(UUID id) { s3.deleteObject(b -> b.bucket(bucket).key(id.toString())); }

    void raderaTomBucket() { s3.deleteBucket(b -> b.bucket(bucket)); }

    @Override public void close() { s3.close(); }
}
