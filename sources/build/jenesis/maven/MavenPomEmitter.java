package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.SequencedProperties;

public class MavenPomEmitter {

    private static final String NAMESPACE_4_0_0 = "http://maven.apache.org/POM/4.0.0";

    private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    public MavenPomEmitter() {
        documentBuilderFactory.setNamespaceAware(true);
    }

    public IOConsumer emit(String groupId,
                           String artifactId,
                           String version,
                           SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies) {
        return emit(groupId, artifactId, version, dependencies, null);
    }

    public IOConsumer emit(String groupId,
                           String artifactId,
                           String version,
                           SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                           Metadata metadata) {
        return emit(groupId, artifactId, version, null, dependencies, Collections.emptyNavigableMap(), metadata);
    }

    public IOConsumer emit(String groupId,
                           String artifactId,
                           String version,
                           String packaging,
                           SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                           SequencedMap<MavenDependencyKey, MavenDependencyValue> managedDependencies,
                           Metadata metadata) {
        Document document;
        try {
            document = documentBuilderFactory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        Element project = (Element) appendChild(document, document, "project");
        project.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance",
                "xsi:schemaLocation",
                "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd");
        appendText(document, project, "modelVersion", "4.0.0");
        appendText(document, project, "groupId", groupId);
        appendText(document, project, "artifactId", artifactId);
        appendText(document, project, "version", version);
        if (packaging != null) {
            appendText(document, project, "packaging", packaging);
        }
        if (metadata != null) {
            if (metadata.name() != null) {
                appendText(document, project, "name", metadata.name());
            }
            if (metadata.description() != null) {
                appendText(document, project, "description", metadata.description());
            }
            if (metadata.url() != null) {
                appendText(document, project, "url", metadata.url());
            }
            if (metadata.inceptionYear() != null) {
                appendText(document, project, "inceptionYear", metadata.inceptionYear());
            }
            if (metadata.organization() != null) {
                Node node = appendChild(document, project, "organization");
                if (metadata.organization().name() != null) {
                    appendText(document, node, "name", metadata.organization().name());
                }
                if (metadata.organization().url() != null) {
                    appendText(document, node, "url", metadata.organization().url());
                }
            }
            if (!metadata.licenses().isEmpty()) {
                Node wrapper = appendChild(document, project, "licenses");
                for (Metadata.License license : metadata.licenses()) {
                    Node node = appendChild(document, wrapper, "license");
                    if (license.name() != null) {
                        appendText(document, node, "name", license.name());
                    }
                    if (license.url() != null) {
                        appendText(document, node, "url", license.url());
                    }
                    if (license.distribution() != null) {
                        appendText(document, node, "distribution", license.distribution());
                    }
                }
            }
            if (!metadata.developers().isEmpty()) {
                Node wrapper = appendChild(document, project, "developers");
                for (Metadata.Developer developer : metadata.developers()) {
                    Node node = appendChild(document, wrapper, "developer");
                    if (developer.id() != null) {
                        appendText(document, node, "id", developer.id());
                    }
                    if (developer.name() != null) {
                        appendText(document, node, "name", developer.name());
                    }
                    if (developer.email() != null) {
                        appendText(document, node, "email", developer.email());
                    }
                    if (developer.url() != null) {
                        appendText(document, node, "url", developer.url());
                    }
                    if (developer.organization() != null) {
                        appendText(document, node, "organization", developer.organization());
                    }
                    if (developer.organizationUrl() != null) {
                        appendText(document, node, "organizationUrl", developer.organizationUrl());
                    }
                    if (!developer.roles().isEmpty()) {
                        Node roles = appendChild(document, node, "roles");
                        developer.roles().forEach(role -> appendText(document, roles, "role", role));
                    }
                    if (developer.timezone() != null) {
                        appendText(document, node, "timezone", developer.timezone());
                    }
                }
            }
            if (metadata.scm() != null) {
                Node node = appendChild(document, project, "scm");
                Metadata.Scm scm = metadata.scm();
                if (scm.connection() != null) {
                    appendText(document, node, "connection", scm.connection());
                }
                String developerConnection = scm.developerConnection() != null
                        ? scm.developerConnection()
                        : scm.connection();
                if (developerConnection != null) {
                    appendText(document, node, "developerConnection", developerConnection);
                }
                if (scm.tag() != null) {
                    appendText(document, node, "tag", scm.tag());
                }
                if (scm.url() != null) {
                    appendText(document, node, "url", scm.url());
                }
            }
            if (metadata.issueManagement() != null) {
                appendManagement(document, project, "issueManagement", metadata.issueManagement());
            }
            if (metadata.ciManagement() != null) {
                appendManagement(document, project, "ciManagement", metadata.ciManagement());
            }
        }
        if (!managedDependencies.isEmpty()) {
            Node wrapper = appendChild(document, appendChild(document, project, "dependencyManagement"), "dependencies");
            managedDependencies.forEach((key, value) -> appendDependency(document, wrapper, key, value));
        }
        if (!dependencies.isEmpty()) {
            Node wrapper = appendChild(document, project, "dependencies");
            dependencies.forEach((key, value) -> appendDependency(document, wrapper, key, value));
        }
        Transformer transformer;
        try {
            transformer = transformerFactory.newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException(e);
        }
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        return writer -> {
            StringWriter buffer = new StringWriter();
            try {
                transformer.transform(new DOMSource(document), new StreamResult(buffer));
            } catch (TransformerException e) {
                throw new IOException(e);
            }
            writer.write(buffer.toString().replace("\r\n", "\n"));
        };
    }

    private static void appendDependency(Document document,
                                         Node wrapper,
                                         MavenDependencyKey key,
                                         MavenDependencyValue value) {
        Node node = appendChild(document, wrapper, "dependency");
        appendText(document, node, "groupId", key.groupId());
        appendText(document, node, "artifactId", key.artifactId());
        if (value.version() != null) {
            appendText(document, node, "version", value.version());
        }
        if (!Objects.equals(key.type(), "jar")) {
            appendText(document, node, "type", key.type());
        }
        if (key.classifier() != null) {
            appendText(document, node, "classifier", key.classifier());
        }
        if (value.scope() != null && value.scope() != MavenDependencyScope.COMPILE) {
            appendText(document, node, "scope", switch (value.scope()) {
                case PROVIDED -> "provided";
                case RUNTIME -> "runtime";
                case TEST -> "test";
                case SYSTEM -> "system";
                case IMPORT -> "import";
                default -> throw new IllegalStateException("Unexpected scope: " + value.scope());
            });
        }
        if (value.systemPath() != null) {
            appendText(document, node, "systemPath", value.systemPath().toString());
        }
        if (value.optional() != null) {
            appendText(document, node, "optional", value.optional().toString());
        }
        if (value.exclusions() != null) {
            Node exclusions = appendChild(document, node, "exclusions");
            value.exclusions().forEach(name -> {
                Node exclusion = appendChild(document, exclusions, "exclusion");
                appendText(document, exclusion, "groupId", name.groupId());
                appendText(document, exclusion, "artifactId", name.artifactId());
            });
        }
    }

    private static void appendManagement(Document document, Node project, String name, Metadata.Management management) {
        Node node = appendChild(document, project, name);
        if (management.system() != null) {
            appendText(document, node, "system", management.system());
        }
        if (management.url() != null) {
            appendText(document, node, "url", management.url());
        }
    }

    private static Node appendChild(Document document, Node parent, String name) {
        return parent.appendChild(document.createElementNS(NAMESPACE_4_0_0, name));
    }

    private static void appendText(Document document, Node parent, String name, String text) {
        appendChild(document, parent, name).setTextContent(text);
    }

    @FunctionalInterface
    public interface IOConsumer {

        void accept(Writer writer) throws IOException;
    }

    public record Metadata(
            String name,
            String description,
            String url,
            List<License> licenses,
            List<Developer> developers,
            Scm scm,
            Organization organization,
            Management issueManagement,
            Management ciManagement,
            String inceptionYear
    ) implements Serializable {

        public Metadata {
            licenses = licenses == null ? List.of() : List.copyOf(licenses);
            developers = developers == null ? List.of() : List.copyOf(developers);
        }

        public Metadata(String name,
                        String description,
                        String url,
                        List<License> licenses,
                        List<Developer> developers,
                        Scm scm,
                        Organization organization) {
            this(name, description, url, licenses, developers, scm, organization, null, null, null);
        }

        static Metadata of(SequencedProperties metadata) {
            if (metadata.isEmpty()) {
                return null;
            }
            SequencedSet<String> licenseIds = new LinkedHashSet<>(), developerIds = new LinkedHashSet<>();
            for (String key : metadata.stringPropertyNames()) {
                int dot = key.lastIndexOf('.');
                if (key.startsWith("license.") && dot > "license.".length()) {
                    licenseIds.add(key.substring("license.".length(), dot));
                } else if (key.startsWith("developer.") && dot > "developer.".length()) {
                    developerIds.add(key.substring("developer.".length(), dot));
                }
            }
            List<License> licenses = new ArrayList<>();
            for (String id : licenseIds) {
                licenses.add(new License(
                        metadata.getProperty("license." + id + ".name"),
                        metadata.getProperty("license." + id + ".url"),
                        metadata.value("license." + id + ".distribution")));
            }
            List<Developer> developers = new ArrayList<>();
            for (String id : developerIds) {
                String declared = metadata.getProperty("developer." + id + ".id");
                String prefix = "developer." + id + ".";
                List<String> roles = metadata.entries(prefix + "roles");
                developers.add(new Developer(
                        declared == null ? id : declared.isBlank() ? null : declared.strip(),
                        metadata.getProperty(prefix + "name"),
                        metadata.getProperty(prefix + "email"),
                        metadata.value(prefix + "url"),
                        metadata.value(prefix + "organization"),
                        metadata.value(prefix + "organizationUrl"),
                        roles == null ? List.of() : roles,
                        metadata.value(prefix + "timezone")));
            }
            Scm scm = null;
            String scmConnection = metadata.getProperty("scm.connection");
            String scmDeveloperConnection = metadata.getProperty("scm.developerConnection");
            String scmUrl = metadata.getProperty("scm.url");
            String scmTag = metadata.value("scm.tag");
            if (scmConnection != null || scmDeveloperConnection != null || scmUrl != null || scmTag != null) {
                scm = new Scm(
                        scmConnection,
                        scmDeveloperConnection,
                        scmUrl,
                        scmTag);
            }
            return new Metadata(
                    metadata.getProperty("name"),
                    metadata.getProperty("description"),
                    metadata.getProperty("url"),
                    licenses,
                    developers,
                    scm,
                    metadata.value("organization.name") == null && metadata.value("organization.url") == null
                            ? null
                            : new Organization(metadata.value("organization.name"), metadata.value("organization.url")),
                    Management.of(metadata, "issueManagement"),
                    Management.of(metadata, "ciManagement"),
                    metadata.value("inceptionYear"));
        }

        public record License(String name, String url, String distribution) implements Serializable {

            public License(String name, String url) {
                this(name, url, null);
            }
        }

        public record Developer(String id,
                                String name,
                                String email,
                                String url,
                                String organization,
                                String organizationUrl,
                                List<String> roles,
                                String timezone) implements Serializable {

            public Developer {
                roles = roles == null ? List.of() : List.copyOf(roles);
            }

            public Developer(String id, String name, String email) {
                this(id, name, email, null, null, null, List.of(), null);
            }
        }

        public record Scm(String connection, String developerConnection, String url, String tag) implements Serializable {
        }

        public record Organization(String name, String url) implements Serializable {
        }

        public record Management(String system, String url) implements Serializable {

            private static Management of(SequencedProperties metadata, String kind) {
                String system = metadata.value(kind + ".system"), url = metadata.value(kind + ".url");
                return system == null && url == null ? null : new Management(system, url);
            }
        }
    }
}
