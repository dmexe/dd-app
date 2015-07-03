require 'test_helper'

class CertGenerateRequestTest < ActiveSupport::TestCase
  UUID = "c65e1fe0-2191-11e5-9250-0002a5d5c51b"

  test "root_ca successfuly load" do
    assert CertGenerateRequest.root_ca
    assert_equal CertGenerateRequest.root_ca.object_id, CertGenerateRequest.root_ca.object_id
  end

  test "root_ca_key successfuly load" do
    assert CertGenerateRequest.root_ca_key
    assert_equal CertGenerateRequest.root_ca_key.object_id, CertGenerateRequest.root_ca_key.object_id
  end

  test "invoke success" do
    m = CertGenerateRequest.new UUID, "default"
    re = m.invoke

    assert re

    assert_not_empty re.cert.to_pem
    assert_not_empty re.cert.to_pem

    assert_equal re.cert.subject.to_s, "/DC=io/DC=vexor/CN=io.vexor.docker.#{UUID}.default"
  end

end
