# {{jreleaserCreationStamp}}
{{#brewRequireRelative}}
require_relative "{{.}}"
{{/brewRequireRelative}}

class {{brewFormulaName}} < Formula
  desc "{{projectDescription}}"
  homepage "{{projectLinkHomepage}}"
  url "{{distributionUrl}}"{{#brewDownloadStrategy}}, :using => {{.}}{{/brewDownloadStrategy}}
  version "{{projectVersion}}"
  sha256 "{{distributionChecksumSha256}}"
  license "{{projectLicense}}"

  {{#brewHasLivecheck}}
  livecheck do
    {{#brewLivecheck}}
    {{.}}
    {{/brewLivecheck}}
  end
  {{/brewHasLivecheck}}
  {{#brewDependencies}}
  depends_on {{.}}
  {{/brewDependencies}}

  def install
    libexec.install Dir["*"]
    Dir["#{libexec}/bin/*"].each do |command|
      name = File.basename(command)
      next if name.end_with?(".bat")
      if name == "jenesis-switch"
        # sourced by the calling shell, so it is linked rather than wrapped in an exec
        bin.install_symlink command => name
      else
        bin.write_exec_script command
      end
    end
  end

  test do
    system bin/"jenesis-init"
    assert_predicate testpath/"build/jenesis/jenesis.version", :exist?
    assert_match version.to_s, shell_output("#{bin}/jenesis-version")
  end
end
