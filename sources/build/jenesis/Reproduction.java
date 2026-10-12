package build.jenesis;

import module java.base;

record Reproduction(Path first, SequencedSet<String> firstSteps, Path second, SequencedSet<String> secondSteps) {

    private static final String CHECKSUM = "checksum", ARGUMENT = "argument.", PROPERTIES = ".properties";

    String verify() throws IOException {
        SortedSet<String> steps = new TreeSet<>(firstSteps);
        steps.addAll(secondSteps);
        List<String> causes = new ArrayList<>(), single = new ArrayList<>();
        SortedMap<String, Outcome> handed = new TreeMap<>(), compared = new TreeMap<>();
        Set<SortedMap<String, String>> unreproducible = new HashSet<>();
        int declared = 0;
        for (String step : steps) {
            if (!firstSteps.contains(step) || !secondSteps.contains(step)) {
                single.add(step + " (" + (firstSteps.contains(step) ? "first" : "second") + " build only)");
                continue;
            }
            Path left = first.resolve(step).resolve(CHECKSUM), right = second.resolve(step).resolve(CHECKSUM);
            SequencedProperties leftStep = properties(left.resolve("step" + PROPERTIES)),
                    rightStep = properties(right.resolve("step" + PROPERTIES));
            Outcome outcome = new Outcome(digests(left.resolve("output" + PROPERTIES)),
                    digests(right.resolve("output" + PROPERTIES)),
                    arguments(left),
                    arguments(right));
            if (!leftStep.flag("reproduce", true) || !rightStep.flag("reproduce", true)) {
                declared++;
                unreproducible.add(outcome.first());
                unreproducible.add(outcome.second());
            } else if (outcome.first().equals(outcome.second())) {
                compared.put(step, outcome);
            } else if (outcome.firstArguments().equals(outcome.secondArguments())) {
                SortedSet<String> files = new TreeSet<>(outcome.first().keySet());
                files.addAll(outcome.second().keySet());
                files.removeIf(file -> Objects.equals(outcome.first().get(file), outcome.second().get(file)));
                causes.add(step + ": " + String.join(", ", files)
                        + (Objects.equals(leftStep.getProperty("serialization"), rightStep.getProperty("serialization"))
                        ? ""
                        : " - the step itself differs between the builds, as it holds a value that differs"
                        + " between them, such as a path into the target folder"));
            } else {
                handed.put(step, outcome);
            }
        }
        boolean inherited;
        do {
            inherited = handed.values().removeIf(outcome -> {
                boolean following = outcome.firstArguments().keySet().stream()
                        .filter(key -> !outcome.firstArguments().get(key).equals(outcome.secondArguments().get(key)))
                        .allMatch(key -> unreproducible.contains(outcome.firstArguments().get(key))
                                && unreproducible.contains(outcome.secondArguments().get(key)));
                if (following) {
                    unreproducible.add(outcome.first());
                    unreproducible.add(outcome.second());
                }
                return following;
            });
        } while (inherited);
        if (causes.isEmpty() && handed.isEmpty() && single.isEmpty()) {
            MessageDigest fingerprint;
            try {
                fingerprint = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            compared.forEach((step, outcome) -> outcome.first().forEach((file, digest) -> fingerprint.update(
                    (step + "/" + file + " " + digest + "\n").getBytes(StandardCharsets.UTF_8))));
            int left = steps.size() - compared.size();
            return "The build reproduced: " + compared.size() + " step" + (compared.size() == 1 ? "" : "s")
                    + " wrote the same output into " + second + " as into " + first
                    + (left == 0
                    ? ""
                    : ", beside " + left + " step" + (left == 1 ? "" : "s") + " whose output is not reproducible"
                    + " by nature" + (left == declared ? "" : " or follows from such a step"))
                    + " - fingerprint SHA-256:" + HexFormat.of().formatHex(fingerprint.digest());
        }
        StringBuilder message = new StringBuilder("The build did not reproduce: building again into ")
                .append(second)
                .append(" wrote a different output than building into ")
                .append(first);
        if (!causes.isEmpty()) {
            message.append("\nCaused by these steps, whose output differs although they were handed the same input"
                    + " in both builds, in the files named:");
            causes.forEach(cause -> message.append("\n  ").append(cause));
        }
        if (!handed.isEmpty()) {
            message.append("\nAs a consequence, these steps were handed a different input:");
            handed.keySet().forEach(consequence -> message.append("\n  ").append(consequence));
        }
        if (!single.isEmpty()) {
            message.append("\nThese steps ran in one of the builds only:");
            single.forEach(step -> message.append("\n  ").append(step));
        }
        throw new IllegalStateException(message.toString());
    }

    private static SequencedProperties properties(Path file) throws IOException {
        return Files.isRegularFile(file) ? SequencedProperties.ofFiles(file) : new SequencedProperties();
    }

    private static SortedMap<String, String> digests(Path file) throws IOException {
        SortedMap<String, String> digests = new TreeMap<>();
        SequencedProperties properties = properties(file);
        for (String name : properties.stringPropertyNames()) {
            StringJoiner joined = new StringJoiner("/");
            Path.of(name).forEach(element -> joined.add(element.toString()));
            digests.put(joined.toString(), properties.getProperty(name));
        }
        return digests;
    }

    private record Outcome(SortedMap<String, String> first,
                           SortedMap<String, String> second,
                           SortedMap<String, SortedMap<String, String>> firstArguments,
                           SortedMap<String, SortedMap<String, String>> secondArguments) {
    }

    private static SortedMap<String, SortedMap<String, String>> arguments(Path checksum) throws IOException {
        SortedMap<String, SortedMap<String, String>> arguments = new TreeMap<>();
        if (Files.isDirectory(checksum)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(checksum, ARGUMENT + "*" + PROPERTIES)) {
                for (Path file : stream) {
                    arguments.put(file.getFileName().toString(), digests(file));
                }
            }
        }
        return arguments;
    }
}
