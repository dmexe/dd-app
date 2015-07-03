class CreateCerts < ActiveRecord::Migration
  def change
    create_table :certs, id: :uuid do |t|
      t.uuid     :user_id,    null: false
      t.string   :role,       null: false
      t.text     :cert_pem,   null: false
      t.text     :key_pem,    null: false
      t.datetime :created_at, null: false
      t.integer  :version,    null: false
      t.datetime :expired_at, null: false
    end
    add_index :certs, [:user_id, :version], unique: true
    add_foreign_key :certs, :users, name: "certs_user_id_fk", on_delete: :restrict
  end
end
