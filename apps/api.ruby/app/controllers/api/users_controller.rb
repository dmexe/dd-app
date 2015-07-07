class Api::UsersController < ApplicationController

  include RandomToken

  before_filter :company_role_required

  def update
    user = users.find_by email: email
    if user
      render_user user
    else
      User.with_lock email do
        user = users.find_or_initialize_by email: email

        if user.new_record?
          user.token = random_token
          user.save
        end

        if user.persisted?
          render_user user
        else
          render_model_errors user
        end

      end
    end
  end

  private

    def render_user(user)
      render json: {
        id:    user.id,
        email: user.email,
        token: user.token
      }
    end

    def users
      current_user.children
    end

    def email
      params[:id]
    end

end
