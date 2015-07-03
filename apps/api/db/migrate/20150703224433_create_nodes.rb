class CreateNodes < ActiveRecord::Migration
  def change
    create_table :nodes, id: :uuid do |t|
      t.uuid       :user_id,    null: false
      t.integer    :status,     null: false, default: 0
      t.string     :role,       null: false
      t.integer    :version,    null: false, default: 0
      t.datetime   :updated_at, null: false
      t.datetime   :created_at, null: false
    end
    add_index :nodes, [:user_id, :role, :version], unique: true
    add_foreign_key :nodes, :users, name: "nodes_user_id_fk", on_delete: :restrict
  end
end
