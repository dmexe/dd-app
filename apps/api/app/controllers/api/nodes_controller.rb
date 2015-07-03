class Api::NodesController < ApplicationController

  def update
    node = user_nodes.first
    if node
      node.touch
      render_node node
    else
      current_user.with_lock scope: role do
        node = user_nodes.first_or_initialize

        if node.new_record?
          node.update(
            role:    role,
            version: user_nodes.count + 1,
          )
        end

        if node.persisted?
          render_node node
        else
          render_model_errors(node)
        end
      end
    end
  end

  private
    def render_node(node)
      render json: {
        id:     node.id,
        role:   role,
        status: node.status,
      }
    end

    def role
      params[:id]
    end

    def user_nodes
      current_user.nodes.where(role: role).order("version DESC")
    end

end
