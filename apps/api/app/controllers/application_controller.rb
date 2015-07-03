class ApplicationController < ActionController::API

  before_filter :authorize_user

  private

    def authorize_user
      current_user || access_denied
    end

    def company_role_required
      current_user.role == User::ROLE_COMPANY || access_denied
    end

    def access_denied
      render status: :forbidden, json: {
        errors: ["Forbidden"]
      }
      false
    end

    def current_user
      @current_user ||= authorize_by_token
    end

    def authorize_by_token
      token = request.headers['X-TOKEN']
      token && User.find_by(token: token)
    end

    def render_model_errors(model)
      json = {
        errors: model.errors.full_messages
      }
      render json: json, status: :bad_request
    end

end
