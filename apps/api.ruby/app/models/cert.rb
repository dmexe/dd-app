class Cert < ActiveRecord::Base
  include ValidateRole
  include WithLock

  validates :user_id, :role, :cert_pem, :key_pem, :expired_at, :version, presence: true
  validates :version, uniqueness: { scope: :user_id }
end
