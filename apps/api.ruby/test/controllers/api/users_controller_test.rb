require 'test_helper'

class Api::UsersControllerTest < ActionController::TestCase
  test "create a new user" do
    email   = 'user@example.com'
    company = User.create! email: "company@example.com", token: 'token', role: User::ROLE_COMPANY

    @request.headers['X-TOKEN'] = company.token
    put :update, id: email

    assert_response :ok
    assert_equal    company.children.count, 1
  end

  test "fetch existing user" do
    email   = 'user@example.com'
    company = User.create! email: "company@example.com", token: 'token', role: User::ROLE_COMPANY
    company.children.create! email: email, token: 'token'

    @request.headers['X-TOKEN'] = company.token
    put :update, id: email

    assert_response :ok
    assert_equal    company.children.count, 1
  end

  test 'failed when not a company' do
    user  = User.create! email: "company@example.com", token: 'token'
    @request.headers['X-TOKEN'] = user.token
    put :update, id: 'user@example.com'

    assert_response 403
    assert_equal @response.body, "{\"errors\":[\"Forbidden\"]}"
  end
end
