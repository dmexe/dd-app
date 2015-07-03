require 'openssl'

CertGenerateRequest = Struct.new(:user_id, :role) do
  Result = Struct.new(:cert, :key)

  class << self
    def root_ca
      @root_ca ||=
        begin
          file = File.read ENV['CA_PEM_FILE']
          OpenSSL::X509::Certificate.new file
        end
    end

    def root_ca_key
      @root_ca_key ||=
        begin
          file = File.read ENV['CA_KEY_FILE']
          OpenSSL::PKey::RSA.new file, ENV['CA_KEY_PASS']
        end
    end
  end

  def invoke
    Result.new(new_certificate, new_certificate_key)
  end

  private

    def new_certificate_key
      @new_certificate_key ||= OpenSSL::PKey::RSA.new 2048
    end

    def new_certificate
      @new_certificate ||=
        begin
          cert = OpenSSL::X509::Certificate.new
          cert.version    = 2
          cert.serial     = 2
          cert.subject    = OpenSSL::X509::Name.parse "/DC=io/DC=vexor/CN=io.vexor.docker.#{user_id}.#{role}"
          cert.issuer     = self.class.root_ca.subject # root CA is the issuer
          cert.public_key = new_certificate_key.public_key
          cert.not_before = 1.day.ago
          cert.not_after  = cert.not_before + 3 * 365 * 24 * 60 * 60 # 3 years validity

          ef = OpenSSL::X509::ExtensionFactory.new
          ef.subject_certificate = cert
          ef.issuer_certificate  = self.class.root_ca
          cert.add_extension(ef.create_extension("keyUsage","digitalSignature", true))
          cert.add_extension(ef.create_extension("subjectKeyIdentifier","hash",false))
          cert.sign(self.class.root_ca_key, OpenSSL::Digest::SHA256.new)
          cert
        end
    end
end
