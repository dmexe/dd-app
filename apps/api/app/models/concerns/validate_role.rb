module ValidateRole
  extend ActiveSupport::Concern

  ROLE_RE = /\A[a-z0-9]+\z/

  included do
    validates :role, presence: true
    validates :role, format: { with: ROLE_RE, message: "only allows a-z and 0-9" }
  end
end
