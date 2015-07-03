class Api::CertsController < ApplicationController

  def update
    cert = current_user.certs.where(role: role).order("version DESC").first
    if cert
      render_cert(cert)
    else
      Cert.with_lock current_user.id, scope: role do
        req  = CertGenerateRequest.new(current_user.id, role).invoke
        cert = current_user.certs.build(
          role:       role,
          cert_pem:   req.cert.to_pem,
          key_pem:    req.key.to_pem,
          version:    current_user.certs.count + 1,
          expired_at: req.cert.not_after,
        )
        if cert.save
          render_cert(cert)
        else
          render_model_errors(cert)
        end
      end
    end
  end

  private

    def render_cert(cert)
      json = {
        role:    cert.role,
        cert:    cert.cert_pem,
        key:     cert.key_pem,
        version: cert.version,
      }
      render json: json
    end

    def role
      @role ||= params[:id]
    end

end
