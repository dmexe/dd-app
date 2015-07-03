class Node < ActiveRecord::Base

  include ValidateRole
  include WithLock

  validates :user_id, :version, presence: true
  validates :version, uniqueness: { scope: [:user_id, :role] }

end
