Rails.application.routes.draw do
  UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/.freeze
  ROLE_RE = /[a-z0-9]+/.freeze

  namespace :api do
    scope '/v1' do
      resources :users, only: [:update]
      resources :certs, only: [:update], constraints: { id: ROLE_RE }
      resources :nodes, only: [:update], constraints: { id: ROLE_RE }
    end
  end

end
