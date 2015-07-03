include RandomToken

user = User.find_or_initialize_by email: 'dima@vexor.io'

user.update!(
  role:      User::ROLE_COMPANY,
  parent_id: nil,
  token:     'token'
)

