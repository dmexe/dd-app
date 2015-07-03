require 'test_helper'

class Api::CertsControllerTest < ActionController::TestCase

  test "create a new cerificate" do
    user = User.create! email: 'me@example.com', token: 'token'

    @request.headers['X-TOKEN'] = user.token
    put :update, id: 'default'
    assert_response :ok
    assert_equal user.certs.count, 1
  end
end
