require 'zlib'

class User < ActiveRecord::Base
  ROLE_MEMBER  = 0
  ROLE_COMPANY = 1

  include WithLock

  has_many :nodes
  has_many :certs
  has_many :children, class_name: 'User', foreign_key: :parent_id

  validates :email, :token, :role, presence: true
  validates :email, uniqueness: true

  validates :role,  inclusion: { in: [0,1] }

end
